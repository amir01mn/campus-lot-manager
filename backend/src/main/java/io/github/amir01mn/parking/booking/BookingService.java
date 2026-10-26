package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.booking.event.BookingCancelled;
import io.github.amir01mn.parking.booking.event.BookingConfirmed;
import io.github.amir01mn.parking.booking.event.BookingExtended;
import io.github.amir01mn.parking.common.BusinessRuleException;
import io.github.amir01mn.parking.common.ConflictException;
import io.github.amir01mn.parking.common.ForbiddenException;
import io.github.amir01mn.parking.common.Money;
import io.github.amir01mn.parking.common.NotFoundException;
import io.github.amir01mn.parking.common.PageResponse;
import io.github.amir01mn.parking.lot.ParkingSpot;
import io.github.amir01mn.parking.lot.ParkingSpotRepository;
import io.github.amir01mn.parking.payment.Payment;
import io.github.amir01mn.parking.payment.PaymentResponse;
import io.github.amir01mn.parking.payment.PaymentService;
import io.github.amir01mn.parking.payment.PaymentStatus;
import io.github.amir01mn.parking.payment.PaymentType;
import io.github.amir01mn.parking.user.Role;
import io.github.amir01mn.parking.user.User;
import io.github.amir01mn.parking.user.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking rules (see "Bookings" in {@code docs/PLAN.md}). Overlaps are checked here for friendly messages; the
 * Postgres exclusion constraints are the backstop.
 *
 * <p>Concurrency: writes that can create an overlap (create, extend) first lock the user row and then the spot
 * row, always in that order. Competing requests for the same spot or user therefore run one after the other and
 * the loser gets the friendly 409 from the overlap check, instead of two inserts deadlocking on the two exclusion
 * constraints.
 *
 * <p>Visibility: a booking is visible to its owner and to managers. Anyone else gets 404, so booking ids do not
 * leak. Owner-only actions (extend, checkout) attempted by a manager get 403.
 */
@Service
public class BookingService {

    static final Duration START_GRACE = Duration.ofMinutes(5);
    static final Duration MIN_DURATION = Duration.ofMinutes(30);
    static final Duration MAX_DURATION = Duration.ofHours(24);
    static final Duration FREE_CANCELLATION_NOTICE = Duration.ofHours(1);
    static final Pattern PLATE = Pattern.compile("^[A-Z0-9 -]{2,10}$");
    static final String SPOT_OVERLAP_CONSTRAINT = "ex_bookings_spot_no_overlap";
    static final String USER_OVERLAP_CONSTRAINT = "ex_bookings_user_no_overlap";

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final ParkingSpotRepository spotRepository;
    private final UserService userService;
    private final PricingService pricingService;
    private final PaymentService paymentService;
    private final BookingCompletionService completionService;
    private final ReferenceCodeGenerator referenceCodes;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository, ParkingSpotRepository spotRepository,
                          UserService userService, PricingService pricingService, PaymentService paymentService,
                          BookingCompletionService completionService, ReferenceCodeGenerator referenceCodes,
                          ApplicationEventPublisher events, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.spotRepository = spotRepository;
        this.userService = userService;
        this.pricingService = pricingService;
        this.paymentService = paymentService;
        this.completionService = completionService;
        this.referenceCodes = referenceCodes;
        this.events = events;
        this.clock = clock;
    }

    /** Creates a CONFIRMED booking and charges the deposit (one hour at the user's current rate). */
    @Transactional
    public BookingResponse create(UUID userId, CreateBookingRequest request) {
        Instant now = clock.instant();
        String plate = normalizePlate(request.plate());
        validateNewWindow(request.start(), request.end(), now);

        User user = userService.requireForUpdate(userId);
        ParkingSpot spot = lockSpot(request.spotId());
        requireBookable(spot);
        requireSpotFree(spot, request.start(), request.end(), null);
        requireUserFree(user.getId(), request.start(), request.end(), null);

        BigDecimal rate = pricingService.hourlyRate(user.getUserType());
        BigDecimal total = pricingService.total(rate, request.start(), request.end());
        BigDecimal deposit = pricingService.deposit(rate);
        String reference = referenceCodes.generateUnique(bookingRepository::existsByReferenceCode);
        Booking booking = new Booking(reference, user, spot, plate, request.start(), request.end(),
                request.paymentMethod(), rate, deposit, total, now);
        try {
            booking = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException ex) {
            throw translateOverlap(ex, spot);
        }
        paymentService.record(booking, PaymentType.DEPOSIT, deposit);
        events.publishEvent(new BookingConfirmed(booking.getId(), reference, user.getId(), spot.getId(),
                booking.getStartTime(), booking.getEndTime(), total, deposit, now));
        log.info("Booking {} confirmed for user {} on spot {}", reference, user.getId(), spot.getLabel());
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listMine(UUID userId, Optional<BookingStatus> status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        Page<Booking> bookings = status
                .map(s -> bookingRepository.findByUserIdAndStatus(userId, s, pageable))
                .orElseGet(() -> bookingRepository.findByUserId(userId, pageable));
        return PageResponse.of(bookings, this::toResponse);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID viewerId, UUID bookingId) {
        return toResponse(loadVisible(userService.require(viewerId), bookingId));
    }

    /** Moves the end later; the total is recalculated from the full new duration at the snapshot rate. */
    @Transactional
    public BookingResponse extend(UUID viewerId, UUID bookingId, Instant newEnd) {
        Instant now = clock.instant();
        Booking booking = loadOwned(userService.require(viewerId), bookingId, "extend");
        requireActive(booking, now);
        Instant previousEnd = booking.getEndTime();
        if (!newEnd.isAfter(previousEnd)) {
            throw new BusinessRuleException("INVALID_NEW_END", "The new end time must be after the current end time.");
        }
        if (Duration.between(booking.getStartTime(), newEnd).compareTo(MAX_DURATION) > 0) {
            throw new BusinessRuleException("BOOKING_TOO_LONG", "A booking can last at most 24 hours.");
        }
        userService.requireForUpdate(booking.getUser().getId());
        ParkingSpot spot = lockSpot(booking.getSpot().getId());
        requireBookable(spot);
        requireSpotFree(spot, previousEnd, newEnd, booking.getId());
        requireUserFree(booking.getUser().getId(), previousEnd, newEnd, booking.getId());

        BigDecimal newTotal = pricingService.total(booking.getHourlyRate(), booking.getStartTime(), newEnd);
        booking.extendTo(newEnd, newTotal);
        try {
            bookingRepository.flush();
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException ex) {
            throw translateOverlap(ex, spot);
        }
        events.publishEvent(new BookingExtended(booking.getId(), booking.getReferenceCode(),
                booking.getUser().getId(), spot.getId(), previousEnd, newEnd, newTotal, now));
        log.info("Booking {} extended to {}", booking.getReferenceCode(), newEnd);
        return toResponse(booking);
    }

    /**
     * Cancels a CONFIRMED booking that has not ended (owner or manager). The deposit is refunded only when the
     * cancellation happens at least one hour before the start.
     */
    @Transactional
    public BookingResponse cancel(UUID viewerId, UUID bookingId) {
        Instant now = clock.instant();
        User viewer = userService.require(viewerId);
        Booking booking = loadVisible(viewer, bookingId);
        requireActive(booking, now);
        boolean refund = qualifiesForRefund(booking, now);
        booking.cancel(now);
        BigDecimal refunded = Money.ZERO;
        if (refund && booking.getDepositAmount().signum() > 0) {
            paymentService.record(booking, PaymentType.REFUND, booking.getDepositAmount());
            refunded = booking.getDepositAmount();
        }
        boolean byOwner = isOwner(booking, viewer);
        events.publishEvent(new BookingCancelled(booking.getId(), booking.getReferenceCode(),
                booking.getUser().getId(), booking.getSpot().getId(), viewer.getId(), byOwner, refunded, now));
        log.info("Booking {} cancelled by {} (refund {})", booking.getReferenceCode(),
                byOwner ? "owner" : "manager " + viewer.getId(), refunded);
        return toResponse(booking);
    }

    /** Owner checkout, allowed once the booking has started; charges {@code total - deposit}. */
    @Transactional
    public BookingResponse checkout(UUID viewerId, UUID bookingId) {
        Instant now = clock.instant();
        Booking booking = loadOwned(userService.require(viewerId), bookingId, "check out of");
        if (!booking.isConfirmed()) {
            throw notActive(booking);
        }
        if (now.isBefore(booking.getStartTime())) {
            throw new BusinessRuleException("CHECKOUT_TOO_EARLY",
                    "You can check out only after the booking has started; cancel it instead.");
        }
        completionService.complete(booking);
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(UUID viewerId, UUID bookingId) {
        Booking booking = loadVisible(userService.require(viewerId), bookingId);
        List<Payment> payments = paymentService.forBooking(booking.getId());
        BigDecimal charged = sum(payments, PaymentType.DEPOSIT).add(sum(payments, PaymentType.FINAL));
        BigDecimal refunded = sum(payments, PaymentType.REFUND);
        User owner = booking.getUser();
        return new ReceiptResponse(toResponse(booking), owner.getFirstName() + " " + owner.getLastName(),
                owner.getEmail(), payments.stream().map(PaymentResponse::of).toList(), Money.of(charged),
                Money.of(refunded), Money.of(charged.subtract(refunded)), pricingService.currency());
    }

    /** Price preview for the caller's tier. Only the window shape is validated (not that it is in the future). */
    @Transactional(readOnly = true)
    public PriceQuote quote(UUID userId, Instant start, Instant end) {
        validateDuration(start, end);
        return pricingService.quote(userService.require(userId).getUserType(), start, end);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rules

    static String normalizePlate(String raw) {
        String plate = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!PLATE.matcher(plate).matches()) {
            throw new BusinessRuleException("INVALID_LICENCE_PLATE",
                    "Licence plates are 2-10 characters: letters, digits, spaces or hyphens.");
        }
        return plate;
    }

    static void validateNewWindow(Instant start, Instant end, Instant now) {
        if (start.isBefore(now.minus(START_GRACE))) {
            throw new BusinessRuleException("START_IN_PAST", "The booking cannot start in the past.");
        }
        validateDuration(start, end);
    }

    static void validateDuration(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("INVALID_TIME_RANGE", "The end time must be after the start time.");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(MIN_DURATION) < 0) {
            throw new BusinessRuleException("BOOKING_TOO_SHORT", "A booking must last at least 30 minutes.");
        }
        if (duration.compareTo(MAX_DURATION) > 0) {
            throw new BusinessRuleException("BOOKING_TOO_LONG", "A booking can last at most 24 hours.");
        }
    }

    /** "Cancelled at least one hour before start" means {@code now <= start - 1h}. */
    static boolean qualifiesForRefund(Booking booking, Instant now) {
        return !now.isAfter(booking.getStartTime().minus(FREE_CANCELLATION_NOTICE));
    }

    private ParkingSpot lockSpot(Long spotId) {
        return spotRepository.findByIdForUpdate(spotId).orElseThrow(() -> NotFoundException.of("Spot", spotId));
    }

    private static void requireBookable(ParkingSpot spot) {
        if (!spot.isBookable()) {
            throw new BusinessRuleException("SPOT_NOT_BOOKABLE",
                    "Spot " + spot.getLabel() + " is not available for booking (disabled or under maintenance).");
        }
    }

    private void requireSpotFree(ParkingSpot spot, Instant start, Instant end, UUID ignoreBookingId) {
        boolean taken = bookingRepository.findConfirmedOverlappingForSpot(spot.getId(), start, end).stream()
                .anyMatch(other -> !other.getId().equals(ignoreBookingId));
        if (taken) {
            throw new ConflictException(spotTakenMessage(spot));
        }
    }

    private void requireUserFree(UUID userId, Instant start, Instant end, UUID ignoreBookingId) {
        bookingRepository.findConfirmedOverlappingForUser(userId, start, end).stream()
                .filter(other -> !other.getId().equals(ignoreBookingId))
                .findFirst()
                .ifPresent(other -> {
                    throw new ConflictException("You already have a confirmed booking (" + other.getReferenceCode()
                            + ") that overlaps this time.");
                });
    }

    private static void requireActive(Booking booking, Instant now) {
        if (!booking.isConfirmed()) {
            throw notActive(booking);
        }
        if (!now.isBefore(booking.getEndTime())) {
            throw new BusinessRuleException("BOOKING_ENDED", "Booking " + booking.getReferenceCode()
                    + " has already ended.");
        }
    }

    private static BusinessRuleException notActive(Booking booking) {
        return new BusinessRuleException("BOOKING_NOT_ACTIVE", "Booking " + booking.getReferenceCode() + " is "
                + booking.getStatus().name().toLowerCase(Locale.ROOT) + ".");
    }

    private static String spotTakenMessage(ParkingSpot spot) {
        return "Spot " + spot.getLabel() + " is already booked for part of that time. Choose another spot or time.";
    }

    /**
     * Maps an exclusion-constraint violation or a lock failure (a concurrent request won the race) to a friendly
     * 409. Anything else is rethrown unchanged.
     */
    private static RuntimeException translateOverlap(DataAccessException ex, ParkingSpot spot) {
        String text = causeChainText(ex);
        if (text.contains(SPOT_OVERLAP_CONSTRAINT)) {
            log.debug("Spot overlap rejected by database constraint", ex);
            return new ConflictException(spotTakenMessage(spot), ex);
        }
        if (text.contains(USER_OVERLAP_CONSTRAINT)) {
            log.debug("User overlap rejected by database constraint", ex);
            return new ConflictException("You already have a confirmed booking that overlaps this time.", ex);
        }
        if (ex instanceof PessimisticLockingFailureException) {
            log.debug("Booking write lost a lock race", ex);
            return new ConflictException("Another booking for this spot was being made at the same time. "
                    + "Please check availability and try again.", ex);
        }
        return ex;
    }

    private static String causeChainText(Throwable ex) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = ex; t != null && text.length() < 10_000; t = t.getCause()) {
            text.append(t.getMessage()).append('\n');
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null) {
                text.append(cve.getConstraintName()).append('\n');
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return text.toString();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Loading and authorization

    private Booking loadVisible(User viewer, UUID bookingId) {
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> NotFoundException.of("Booking", bookingId));
        if (!isOwner(booking, viewer) && !isManager(viewer)) {
            throw NotFoundException.of("Booking", bookingId);
        }
        return booking;
    }

    private Booking loadOwned(User viewer, UUID bookingId, String action) {
        Booking booking = loadVisible(viewer, bookingId);
        if (!isOwner(booking, viewer)) {
            throw new ForbiddenException("Only the booking's owner can " + action + " it.");
        }
        return booking;
    }

    private static boolean isOwner(Booking booking, User viewer) {
        return booking.getUser().getId().equals(viewer.getId());
    }

    private static boolean isManager(User user) {
        return user.getRole() == Role.MANAGER || user.getRole() == Role.SUPER_MANAGER;
    }

    private static BigDecimal sum(List<Payment> payments, PaymentType type) {
        return payments.stream()
                .filter(p -> p.getType() == type && p.getStatus() == PaymentStatus.SUCCEEDED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BookingResponse toResponse(Booking booking) {
        return BookingResponse.of(booking, pricingService.currency());
    }
}
