package io.github.amir01mn.parking.demo;

import io.github.amir01mn.parking.booking.Booking;
import io.github.amir01mn.parking.booking.BookingRepository;
import io.github.amir01mn.parking.booking.PricingService;
import io.github.amir01mn.parking.booking.ReferenceCodeGenerator;
import io.github.amir01mn.parking.lot.ParkingSpot;
import io.github.amir01mn.parking.lot.ParkingSpotRepository;
import io.github.amir01mn.parking.payment.Payment;
import io.github.amir01mn.parking.payment.PaymentMethod;
import io.github.amir01mn.parking.payment.PaymentRepository;
import io.github.amir01mn.parking.payment.PaymentStatus;
import io.github.amir01mn.parking.payment.PaymentType;
import io.github.amir01mn.parking.user.DirectoryService;
import io.github.amir01mn.parking.user.PasswordPolicy;
import io.github.amir01mn.parking.user.Role;
import io.github.amir01mn.parking.user.User;
import io.github.amir01mn.parking.user.UserRepository;
import io.github.amir01mn.parking.user.UserType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code demo} profile only: creates one account per pricing tier plus a manager (all sharing
 * {@code app.demo.password}) and, the first time, a history of completed, cancelled and upcoming bookings with
 * their payments so dashboards have data. Idempotent: existing accounts are left untouched and bookings are only
 * seeded while no demo account has any booking.
 */
@Component
@Profile("demo")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DemoDataSeeder implements ApplicationRunner {

    /** A demo account; a null {@code type} means "look it up in the university directory". */
    record DemoAccount(String email, String firstName, String lastName, Role role, UserType type) {
    }

    static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("student.demo@my.yorku.ca", "Sam", "Student", Role.USER, null),
            new DemoAccount("faculty.demo@yorku.ca", "Fiona", "Faculty", Role.USER, null),
            new DemoAccount("staff.demo@yorku.ca", "Stan", "Staff", Role.USER, null),
            new DemoAccount("visitor.demo@example.com", "Vera", "Visitor", Role.USER, null),
            new DemoAccount("manager.demo@yorku.ca", "Morgan", "Manager", Role.MANAGER, UserType.STAFF));

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String[] LOT_PREFIXES = {"A", "B", "C"};
    private static final int[] PAST_DAYS = {1, 3, 6, 10};

    private final String demoPassword;
    private final UserRepository userRepository;
    private final DirectoryService directoryService;
    private final PasswordEncoder passwordEncoder;
    private final BookingRepository bookingRepository;
    private final ParkingSpotRepository spotRepository;
    private final PaymentRepository paymentRepository;
    private final PricingService pricingService;
    private final ReferenceCodeGenerator referenceCodes;
    private final Clock clock;

    public DemoDataSeeder(@Value("${app.demo.password:}") String demoPassword, UserRepository userRepository,
                          DirectoryService directoryService, PasswordEncoder passwordEncoder,
                          BookingRepository bookingRepository, ParkingSpotRepository spotRepository,
                          PaymentRepository paymentRepository, PricingService pricingService,
                          ReferenceCodeGenerator referenceCodes, Clock clock) {
        this.demoPassword = demoPassword;
        this.userRepository = userRepository;
        this.directoryService = directoryService;
        this.passwordEncoder = passwordEncoder;
        this.bookingRepository = bookingRepository;
        this.spotRepository = spotRepository;
        this.paymentRepository = paymentRepository;
        this.pricingService = pricingService;
        this.referenceCodes = referenceCodes;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!PasswordPolicy.isAcceptable(demoPassword)) {
            log.warn("Demo data skipped: app.demo.password is missing or does not meet the password policy");
            return;
        }
        List<User> users = new ArrayList<>();
        for (DemoAccount account : ACCOUNTS) {
            users.add(ensureAccount(account));
        }
        List<User> customers = users.stream().filter(u -> u.getRole() == Role.USER).toList();
        boolean anyBookings = users.stream().anyMatch(u -> bookingRepository.countByUserId(u.getId()) > 0);
        if (anyBookings) {
            log.info("Demo bookings already present; skipping");
            return;
        }
        seedBookings(customers);
    }

    private User ensureAccount(DemoAccount account) {
        String email = User.normalizeEmail(account.email());
        return userRepository.findByEmail(email).orElseGet(() -> {
            UserType type = account.type() != null ? account.type() : directoryService.resolveType(email);
            User user = userRepository.save(new User(email, passwordEncoder.encode(demoPassword),
                    account.firstName(), account.lastName(), type, account.role(), clock.instant()));
            log.info("Created demo account {} ({}, {})", email, type, account.role());
            return user;
        });
    }

    private void seedBookings(List<User> customers) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        PaymentMethod[] methods = PaymentMethod.values();
        int count = 0;
        for (int i = 0; i < customers.size(); i++) {
            User user = customers.get(i);
            BigDecimal rate = pricingService.hourlyRate(user.getUserType());
            PaymentMethod method = methods[i % methods.length];

            // Completed visits in the past.
            for (int d = 0; d < PAST_DAYS.length; d++) {
                int daysAgo = PAST_DAYS[d];
                Instant start = today.minusDays(daysAgo).atTime(8 + 2 * i, 0).atZone(zone).toInstant();
                Instant end = start.plus(Duration.ofMinutes(90L + 60L * ((i + d) % 3)));
                ParkingSpot spot = spot(LOT_PREFIXES[(i + d) % 3], 1 + i * 5 + d);
                Booking booking = newBooking(user, spot, start, end, method, rate, start.minus(Duration.ofDays(1)));
                booking.complete(end);
                bookingRepository.save(booking);
                payment(booking, PaymentType.DEPOSIT, booking.getDepositAmount(), booking.getCreatedAt());
                BigDecimal finalCharge = pricingService.finalCharge(booking.getTotalAmount(),
                        booking.getDepositAmount());
                if (finalCharge.signum() > 0) {
                    payment(booking, PaymentType.FINAL, finalCharge, end);
                }
                count++;
            }

            // One early cancellation with a refunded deposit.
            Instant cancelledStart = today.minusDays(2).atTime(13, 0).atZone(zone).toInstant();
            Booking cancelled = newBooking(user, spot("C", 20 + i), cancelledStart,
                    cancelledStart.plus(Duration.ofHours(2)), method, rate, cancelledStart.minus(Duration.ofDays(2)));
            Instant cancelledAt = cancelledStart.minus(Duration.ofDays(1));
            cancelled.cancel(cancelledAt);
            bookingRepository.save(cancelled);
            payment(cancelled, PaymentType.DEPOSIT, cancelled.getDepositAmount(), cancelled.getCreatedAt());
            payment(cancelled, PaymentType.REFUND, cancelled.getDepositAmount(), cancelledAt);
            count++;

            // An upcoming confirmed booking tomorrow.
            Instant futureStart = today.plusDays(1).atTime(9 + 2 * i, 0).atZone(zone).toInstant();
            Booking upcoming = newBooking(user, spot("A", 30 + i), futureStart, futureStart.plus(Duration.ofHours(3)),
                    method, rate, now);
            bookingRepository.save(upcoming);
            payment(upcoming, PaymentType.DEPOSIT, upcoming.getDepositAmount(), now);
            count++;
        }
        log.info("Seeded {} demo bookings", count);
    }

    private Booking newBooking(User user, ParkingSpot spot, Instant start, Instant end, PaymentMethod method,
                               BigDecimal rate, Instant createdAt) {
        String reference = referenceCodes.generateUnique(bookingRepository::existsByReferenceCode);
        return new Booking(reference, user, spot, "DEMO " + (100 + Math.floorMod(user.getEmail().hashCode(), 900)),
                start, end, method, rate, pricingService.deposit(rate), pricingService.total(rate, start, end),
                createdAt);
    }

    private void payment(Booking booking, PaymentType type, BigDecimal amount, Instant at) {
        paymentRepository.save(new Payment(booking, type, amount, booking.getPaymentMethod(), PaymentStatus.SUCCEEDED,
                "SEED-" + booking.getReferenceCode() + "-" + type, at));
    }

    private ParkingSpot spot(String lotPrefix, int number) {
        String sensorId = "SNS-%s-%03d".formatted(lotPrefix, number);
        return spotRepository.findBySensorId(sensorId)
                .orElseThrow(() -> new IllegalStateException("Seeded spot " + sensorId + " is missing"));
    }
}
