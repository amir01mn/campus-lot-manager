package io.github.amir01mn.parking.sensor;

import io.github.amir01mn.parking.lot.SpotSensor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Pure generation logic of the demo sensor simulator: picks a random subset of spots and decides each one's
 * occupancy. Spots with a booking covering now are occupied with probability {@code coveredOccupancy}; any other
 * spot with the (small) probability {@code uncoveredOccupancy}, which occasionally produces an unauthorized-parking
 * alert.
 */
public final class SimulatedReadingGenerator {

    private final RandomGenerator random;
    private final double sampleFraction;
    private final double coveredOccupancy;
    private final double uncoveredOccupancy;

    public SimulatedReadingGenerator(RandomGenerator random, double sampleFraction, double coveredOccupancy,
                                     double uncoveredOccupancy) {
        this.random = Objects.requireNonNull(random, "random");
        this.sampleFraction = requireProbability(sampleFraction, "sampleFraction");
        this.coveredOccupancy = requireProbability(coveredOccupancy, "coveredOccupancy");
        this.uncoveredOccupancy = requireProbability(uncoveredOccupancy, "uncoveredOccupancy");
    }

    /**
     * @param spots          every spot with its sensor
     * @param coveredSpotIds spots that have a CONFIRMED booking covering {@code now}
     * @return one reading, timestamped {@code now}, per sampled spot
     */
    public List<SensorReading> generate(List<SpotSensor> spots, Set<Long> coveredSpotIds, Instant now) {
        List<SensorReading> readings = new ArrayList<>();
        for (SpotSensor spot : spots) {
            if (random.nextDouble() >= sampleFraction) {
                continue;
            }
            double p = coveredSpotIds.contains(spot.spotId()) ? coveredOccupancy : uncoveredOccupancy;
            readings.add(new SensorReading(spot.sensorId(), random.nextDouble() < p, now));
        }
        return readings;
    }

    private static double requireProbability(double value, String name) {
        if (!(value >= 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }
}
