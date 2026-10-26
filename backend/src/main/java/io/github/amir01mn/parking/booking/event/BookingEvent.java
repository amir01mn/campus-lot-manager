package io.github.amir01mn.parking.booking.event;

import java.time.Instant;
import java.util.UUID;

/** Domain events published by the booking service (consumed by notification listeners in Phase 3). */
public sealed interface BookingEvent permits BookingConfirmed, BookingExtended, BookingCancelled, BookingCompleted {

    UUID bookingId();

    String referenceCode();

    UUID userId();

    Long spotId();

    Instant occurredAt();
}
