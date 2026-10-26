package io.github.amir01mn.parking.booking;

import java.time.Instant;

/**
 * Filters for the manager's booking search; every field is optional.
 *
 * @param from bookings ending after this instant (overlap with {@code [from, to)})
 * @param to   bookings starting before this instant
 * @param q    case-insensitive substring of the reference code or licence plate
 */
public record BookingSearchCriteria(BookingStatus status, Long lotId, Instant from, Instant to, String q) {
}
