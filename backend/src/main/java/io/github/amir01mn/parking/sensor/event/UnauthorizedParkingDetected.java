package io.github.amir01mn.parking.sensor.event;

import java.time.Instant;

/**
 * A spot's sensor reported it newly occupied while no CONFIRMED booking covers the current time. Raised once per
 * continuous occupancy (on the unoccupied -> occupied transition). Published inside the ingestion transaction.
 */
public record UnauthorizedParkingDetected(
        Long spotId,
        String spotLabel,
        Long lotId,
        String lotName,
        String sensorId,
        Instant occupiedSince,
        Instant occurredAt) {
}
