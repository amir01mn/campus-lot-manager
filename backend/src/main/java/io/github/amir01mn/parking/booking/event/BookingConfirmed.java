package io.github.amir01mn.parking.booking.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A booking was created and its deposit charged.
 * Published inside the booking transaction; listeners that must only see committed state should use
 * {@code @TransactionalEventListener}.
 */
public record BookingConfirmed(
        UUID bookingId,
        String referenceCode,
        UUID userId,
        Long spotId,
        Instant startTime,
        Instant endTime,
        BigDecimal totalAmount,
        BigDecimal depositAmount,
        Instant occurredAt) implements BookingEvent {
}
