package io.github.amir01mn.parking.sensor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A batch of readings: {@code {"readings": [{sensorId, occupied, timestamp}, ...]}}, 1 to 500 entries. */
public record SensorReadingsRequest(
        @NotNull @Size(min = 1, max = SensorReadingsRequest.MAX_BATCH) List<@NotNull @Valid SensorReading> readings) {

    public static final int MAX_BATCH = 500;
}
