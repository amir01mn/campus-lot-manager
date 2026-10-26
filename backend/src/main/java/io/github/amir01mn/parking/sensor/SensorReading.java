package io.github.amir01mn.parking.sensor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** One occupancy reading from a spot sensor. */
public record SensorReading(
        @NotBlank @Size(max = 50) String sensorId,
        @NotNull Boolean occupied,
        @NotNull Instant timestamp) {
}
