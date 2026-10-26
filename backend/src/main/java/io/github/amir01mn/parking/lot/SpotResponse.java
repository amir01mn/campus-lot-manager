package io.github.amir01mn.parking.lot;

import java.time.Instant;

/** A spot with its manager state, sensor state and the derived {@code bookable} flag. */
public record SpotResponse(
        Long id,
        Long lotId,
        String label,
        boolean enabled,
        boolean maintenanceRequired,
        boolean occupied,
        boolean bookable,
        Instant lastSensorUpdate) {

    public static SpotResponse of(ParkingSpot spot) {
        return new SpotResponse(spot.getId(), spot.getLot().getId(), spot.getLabel(), spot.isEnabled(),
                spot.isMaintenanceRequired(), spot.isOccupied(), spot.isBookable(), spot.getLastSensorUpdate());
    }
}
