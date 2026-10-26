package io.github.amir01mn.parking.lot;

import java.time.Instant;
import java.util.List;

/** Spots in a lot that can be booked for {@code [start, end)}. */
public record AvailabilityResponse(Long lotId, Instant start, Instant end, int availableCount,
                                   List<SpotResponse> spots) {
}
