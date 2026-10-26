package io.github.amir01mn.parking.sensor.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A spot is still occupied by the same continuous occupancy more than the grace period after its booking ended.
 * Raised at most once per booking. Published inside the overstay job's transaction.
 */
public record OverstayDetected(
        UUID bookingId,
        String referenceCode,
        UUID userId,
        String licencePlate,
        Long spotId,
        String spotLabel,
        String lotName,
        Instant bookingEnd,
        Instant occurredAt) {
}
