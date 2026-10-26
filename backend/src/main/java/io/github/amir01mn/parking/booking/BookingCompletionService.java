package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.booking.event.BookingCompleted;
import io.github.amir01mn.parking.common.BusinessRuleException;
import io.github.amir01mn.parking.payment.PaymentService;
import io.github.amir01mn.parking.payment.PaymentType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completes bookings: records the FINAL charge ({@code total - deposit}, if positive), marks the booking
 * COMPLETED and publishes {@link BookingCompleted}. Shared by owner checkout and the scheduled auto-checkout.
 */
@Service
public class BookingCompletionService {

    private static final Logger log = LoggerFactory.getLogger(BookingCompletionService.class);

    private final BookingRepository bookingRepository;
    private final PricingService pricingService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingCompletionService(BookingRepository bookingRepository, PricingService pricingService,
                                    PaymentService paymentService, ApplicationEventPublisher events, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.pricingService = pricingService;
        this.paymentService = paymentService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Completes a managed, CONFIRMED booking within the caller's transaction.
     *
     * @return the final charge (zero when the deposit already covers the total)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal complete(Booking booking) {
        if (!booking.isConfirmed()) {
            throw new BusinessRuleException("BOOKING_NOT_ACTIVE",
                    "Booking " + booking.getReferenceCode() + " is " + booking.getStatus() + ".");
        }
        Instant now = clock.instant();
        BigDecimal finalCharge = pricingService.finalCharge(booking.getTotalAmount(), booking.getDepositAmount());
        if (finalCharge.signum() > 0) {
            paymentService.record(booking, PaymentType.FINAL, finalCharge);
        }
        booking.complete(now);
        events.publishEvent(new BookingCompleted(booking.getId(), booking.getReferenceCode(),
                booking.getUser().getId(), booking.getSpot().getId(), finalCharge, booking.getTotalAmount(), now));
        log.info("Completed booking {} (final charge {})", booking.getReferenceCode(), finalCharge);
        return finalCharge;
    }

    /**
     * Entry point for the scheduled auto-checkout: completes the booking in its own transaction if it is still
     * CONFIRMED.
     *
     * @return {@code true} if this call completed the booking
     */
    @Transactional
    public boolean completeIfConfirmed(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .filter(Booking::isConfirmed)
                .map(booking -> {
                    complete(booking);
                    return true;
                })
                .orElse(false);
    }
}
