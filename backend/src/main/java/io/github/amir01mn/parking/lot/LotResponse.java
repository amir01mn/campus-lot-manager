package io.github.amir01mn.parking.lot;

import java.time.Instant;

/** A lot with live spot counts. {@code bookableSpots} is 0 while the lot is disabled. */
public record LotResponse(
        Long id,
        String name,
        String location,
        boolean enabled,
        long totalSpots,
        long bookableSpots,
        long occupiedSpots,
        Instant createdAt) {

    public static LotResponse of(ParkingLot lot, LotSpotCounts counts) {
        long bookable = lot.isEnabled() ? counts.enabledAndServiceable() : 0L;
        return new LotResponse(lot.getId(), lot.getName(), lot.getLocation(), lot.isEnabled(), counts.total(),
                bookable, counts.occupied(), lot.getCreatedAt());
    }
}
