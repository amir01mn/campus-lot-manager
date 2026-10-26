package io.github.amir01mn.parking.lot.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A manager flagged a spot as needing maintenance. {@code affectedUpcomingBookings} counts CONFIRMED bookings on the
 * spot that have not ended (they are kept, not cancelled). Published inside the manager's transaction.
 */
public record MaintenanceFlagged(
        Long spotId,
        String spotLabel,
        Long lotId,
        String lotName,
        UUID flaggedBy,
        long affectedUpcomingBookings,
        Instant occurredAt) {
}
