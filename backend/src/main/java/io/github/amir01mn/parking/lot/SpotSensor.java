package io.github.amir01mn.parking.lot;

/** A spot id with its sensor id (JPQL constructor projection). */
public record SpotSensor(Long spotId, String sensorId) {
}
