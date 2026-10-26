package io.github.amir01mn.parking.sensor;

import java.util.List;

/**
 * Outcome of a batch.
 *
 * @param accepted       readings applied to a spot
 * @param ignoredStale   readings older than the spot's last applied reading
 * @param ignoredFuture  readings timestamped too far in the future (sensor clock skew), not applied
 * @param unknownSensors distinct sensor ids that match no spot, sorted
 */
public record SensorIngestResult(int accepted, int ignoredStale, int ignoredFuture, List<String> unknownSensors) {
}
