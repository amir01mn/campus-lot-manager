package io.github.amir01mn.parking.booking.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A booking was completed (checkout or auto-checkout) and its final charge recorded.
 * Published inside the booking transaction; listeners that must only see committed state should use
 * {@code @TransactionalEventListener}.
 */
public record BookingCompleted(
        UUID bookingId,
        String referenceCode,
        UUID userId,
        Long spotId,
        BigDecimal finalCharge,
        BigDecimal totalAmount,
        Instant occurredAt) implements BookingEvent {
}
