package io.github.amir01mn.parking.notification;

import io.github.amir01mn.parking.booking.event.BookingCancelled;
import io.github.amir01mn.parking.booking.event.BookingCompleted;
import io.github.amir01mn.parking.booking.event.BookingConfirmed;
import io.github.amir01mn.parking.booking.event.BookingExtended;
import io.github.amir01mn.parking.lot.ParkingSpotRepository;
import io.github.amir01mn.parking.lot.event.MaintenanceFlagged;
import io.github.amir01mn.parking.sensor.event.OverstayDetected;
import io.github.amir01mn.parking.sensor.event.UnauthorizedParkingDetected;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer for domain events: turns them into {@link Notification} rows.
 *
 * <p>Every handler runs only after the publishing transaction has committed ({@code AFTER_COMMIT}), so a rolled-back
 * booking or alert never produces a notification, and writes in its own new transaction. A failing handler cannot
 * undo the already-committed business change; Spring logs the exception.
 *
 * <p>Booking events notify the booking's owner (in-app and by e-mail). Alert events notify every manager.
 */
@Component
public class NotificationEventListener {

    private final NotificationService notifications;
    private final ParkingSpotRepository spotRepository;
    private final DateTimeFormatter timeFormat;

    public NotificationEventListener(NotificationService notifications, ParkingSpotRepository spotRepository,
                                     Clock clock) {
        this.notifications = notifications;
        this.spotRepository = spotRepository;
        this.timeFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.CANADA)
                .withZone(clock.getZone());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingConfirmed event) {
        String message = "Booking %s confirmed: %s, %s to %s. Total %s (deposit %s charged)."
                .formatted(event.referenceCode(), spotName(event.spotId()), time(event.startTime()),
                        time(event.endTime()), money(event.totalAmount()), money(event.depositAmount()));
        notifications.notifyBookingOwner(event.userId(), NotificationType.BOOKING_CONFIRMED,
                "Booking " + event.referenceCode() + " confirmed", message, event.bookingId(), event.spotId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingExtended event) {
        String message = "Booking %s extended: %s now ends %s. New total %s."
                .formatted(event.referenceCode(), spotName(event.spotId()), time(event.newEndTime()),
                        money(event.newTotalAmount()));
        notifications.notifyBookingOwner(event.userId(), NotificationType.BOOKING_EXTENDED,
                "Booking " + event.referenceCode() + " extended", message, event.bookingId(), event.spotId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingCancelled event) {
        String who = event.cancelledByOwner() ? "" : " by a parking manager";
        String refund = event.refundAmount().signum() > 0
                ? "Your deposit of " + money(event.refundAmount()) + " has been refunded."
                : "The deposit is kept (cancelled less than 1 hour before the start).";
        String message = "Booking %s (%s) was cancelled%s. %s"
                .formatted(event.referenceCode(), spotName(event.spotId()), who, refund);
        notifications.notifyBookingOwner(event.userId(), NotificationType.BOOKING_CANCELLED,
                "Booking " + event.referenceCode() + " cancelled", message, event.bookingId(), event.spotId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingCompleted event) {
        String charge = event.finalCharge().signum() > 0
                ? "Final charge " + money(event.finalCharge()) + "."
                : "Nothing more to pay; the deposit covered it.";
        String message = "Booking %s (%s) is complete. Total %s. %s"
                .formatted(event.referenceCode(), spotName(event.spotId()), money(event.totalAmount()), charge);
        notifications.notifyBookingOwner(event.userId(), NotificationType.BOOKING_COMPLETED,
                "Booking " + event.referenceCode() + " completed", message, event.bookingId(), event.spotId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(OverstayDetected event) {
        String message = "Overstay: spot %s (%s) is still occupied after booking %s (plate %s) ended at %s."
                .formatted(event.spotLabel(), event.lotName(), event.referenceCode(), event.licencePlate(),
                        time(event.bookingEnd()));
        notifications.notifyManagers(NotificationType.OVERSTAY_DETECTED, message, event.bookingId(),
                event.spotId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(UnauthorizedParkingDetected event) {
        String message = "Unauthorized parking: spot %s (%s) became occupied at %s with no confirmed booking."
                .formatted(event.spotLabel(), event.lotName(), time(event.occupiedSince()));
        notifications.notifyManagers(NotificationType.UNAUTHORIZED_PARKING, message, null, event.spotId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(MaintenanceFlagged event) {
        String upcoming = event.affectedUpcomingBookings() == 0 ? "No upcoming bookings are affected."
                : event.affectedUpcomingBookings() + " upcoming confirmed booking(s) remain on this spot.";
        String message = "Maintenance: spot %s (%s) was flagged for maintenance. %s"
                .formatted(event.spotLabel(), event.lotName(), upcoming);
        notifications.notifyManagers(NotificationType.MAINTENANCE_FLAGGED, message, null, event.spotId());
    }

    private String spotName(Long spotId) {
        return spotRepository.findById(spotId)
                .map(spot -> "spot " + spot.getLabel() + " in " + spot.getLot().getName())
                .orElse("spot " + spotId);
    }

    private String time(Instant instant) {
        return timeFormat.format(instant);
    }

    private static String money(BigDecimal amount) {
        return "$" + amount.toPlainString();
    }
}
