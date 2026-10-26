package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.lot.ParkingSpot;
import io.github.amir01mn.parking.payment.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A booking as returned by the API. Amounts are in {@code currency}. */
public record BookingResponse(
        UUID id,
        String referenceCode,
        BookingStatus status,
        UUID userId,
        SpotSummary spot,
        String licencePlate,
        Instant startTime,
        Instant endTime,
        PaymentMethod paymentMethod,
        BigDecimal hourlyRate,
        BigDecimal depositAmount,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        Instant cancelledAt,
        Instant completedAt) {

    public record SpotSummary(Long id, String label, Long lotId, String lotName) {
    }

    /** Requires the booking's spot and lot to be loaded (call inside a transaction). */
    public static BookingResponse of(Booking booking, String currency) {
        ParkingSpot spot = booking.getSpot();
        return new BookingResponse(booking.getId(), booking.getReferenceCode(), booking.getStatus(),
                booking.getUser().getId(),
                new SpotSummary(spot.getId(), spot.getLabel(), spot.getLot().getId(), spot.getLot().getName()),
                booking.getLicencePlate(), booking.getStartTime(), booking.getEndTime(), booking.getPaymentMethod(),
                booking.getHourlyRate(), booking.getDepositAmount(), booking.getTotalAmount(), currency,
                booking.getCreatedAt(), booking.getCancelledAt(), booking.getCompletedAt());
    }
}
