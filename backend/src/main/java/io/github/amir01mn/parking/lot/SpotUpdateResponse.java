package io.github.amir01mn.parking.lot;

/**
 * The updated spot. {@code affectedUpcomingBookings} counts CONFIRMED bookings on the spot that have not ended, when
 * the spot is now unbookable (they are kept, not cancelled); it is 0 while the spot is bookable.
 */
public record SpotUpdateResponse(SpotResponse spot, long affectedUpcomingBookings) {
}
