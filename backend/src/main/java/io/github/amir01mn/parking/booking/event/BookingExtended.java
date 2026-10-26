package io.github.amir01mn.parking.booking.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A confirmed booking's end time moved later and its total was recalculated.
 * Published inside the booking transaction; listeners that must only see committed state should use
 * {@code @TransactionalEventListener}.
 */
public record BookingExtended(
        UUID bookingId,
        String referenceCode,
        UUID userId,
        Long spotId,
        Instant previousEndTime,
        Instant newEndTime,
        BigDecimal newTotalAmount,
        Instant occurredAt) implements BookingEvent {
}
