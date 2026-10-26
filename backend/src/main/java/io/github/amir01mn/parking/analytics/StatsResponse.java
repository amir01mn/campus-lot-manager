package io.github.amir01mn.parking.analytics;

import io.github.amir01mn.parking.booking.BookingStatus;
import io.github.amir01mn.parking.user.UserType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;

/**
 * Manager dashboard figures for {@code [from, to)}. Spot figures are live (now); everything else is for the range.
 *
 * @param bookingsCount     bookings starting in the range, any status
 * @param bookingsByStatus  the same bookings per status (every status present, zero-filled)
 * @param revenue           succeeded DEPOSIT + FINAL - REFUND payments made in the range
 * @param bookingsPerDay    bookings by start date in {@code timeZone}, one entry per day of the range (zero-filled)
 * @param revenueByUserType revenue split by the paying user's tier (every tier present)
 * @param lotUtilisation    per lot: booked hours (non-cancelled bookings, clipped to the range) over spot-hours
 */
public record StatsResponse(
        Instant from,
        Instant to,
        String timeZone,
        String currency,
        long totalSpots,
        long occupiedSpots,
        BigDecimal occupancyPercent,
        long bookingsCount,
        Map<BookingStatus, Long> bookingsByStatus,
        BigDecimal revenue,
        List<DailyBookings> bookingsPerDay,
        List<UserTypeRevenue> revenueByUserType,
        List<LotUtilisation> lotUtilisation) {

    public record DailyBookings(LocalDate date, long count) {
    }

    public record UserTypeRevenue(UserType userType, BigDecimal revenue) {
    }

    public record LotUtilisation(
            Long lotId,
            String lotName,
            long totalSpots,
            BigDecimal bookedHours,
            BigDecimal availableSpotHours,
            BigDecimal utilisationPercent) {
    }
}
