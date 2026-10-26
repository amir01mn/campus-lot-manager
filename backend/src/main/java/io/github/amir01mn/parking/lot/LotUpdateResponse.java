package io.github.amir01mn.parking.lot;

/**
 * The updated lot. {@code affectedUpcomingBookings} counts CONFIRMED bookings in the lot that have not ended, when the
 * lot is now disabled (they are kept, not cancelled); it is 0 while the lot is enabled.
 */
public record LotUpdateResponse(LotResponse lot, long affectedUpcomingBookings) {
}
