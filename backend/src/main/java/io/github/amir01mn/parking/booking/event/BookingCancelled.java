package io.github.amir01mn.parking.booking.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A confirmed booking was cancelled. {@code refundAmount} is zero when the deposit was kept.
 * Published inside the booking transaction; listeners that must only see committed state should use
 * {@code @TransactionalEventListener}.
 */
public record BookingCancelled(
        UUID bookingId,
        String referenceCode,
        UUID userId,
        Long spotId,
        UUID cancelledBy,
        boolean cancelledByOwner,
        BigDecimal refundAmount,
        Instant occurredAt) implements BookingEvent {
}
