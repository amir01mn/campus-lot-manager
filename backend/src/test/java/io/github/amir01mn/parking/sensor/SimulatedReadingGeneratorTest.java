package io.github.amir01mn.parking.sensor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.amir01mn.parking.lot.SpotSensor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class SimulatedReadingGeneratorTest {

    private static final Instant NOW = Instant.parse("2030-03-01T14:00:00Z");

    private static List<SpotSensor> spots(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> new SpotSensor(id, "SNS-X-" + id)).toList();
    }

    @Test
    void samplesAboutTheConfiguredFractionWithTimestampNow() {
        SimulatedReadingGenerator generator = new SimulatedReadingGenerator(new Random(42), 0.25, 0.85, 0.0);

        List<SensorReading> readings = generator.generate(spots(4000), Set.of(), NOW);

        assertThat(readings.size()).isBetween(850, 1150);
        assertThat(readings).allSatisfy(r -> assertThat(r.timestamp()).isEqualTo(NOW));
        assertThat(readings).extracting(SensorReading::sensorId).doesNotHaveDuplicates();
        assertThat(readings).noneMatch(SensorReading::occupied); // uncoveredOccupancy = 0
    }

    @Test
    void coveredSpotsAreMostlyOccupiedAndOthersRarely() {
        List<SpotSensor> all = spots(10_000);
        Set<Long> covered = new java.util.HashSet<>();
        for (long id = 1; id <= 5000; id++) {
            covered.add(id);
        }
        SimulatedReadingGenerator generator = new SimulatedReadingGenerator(new Random(7), 1.0, 0.85, 0.01);

        List<SensorReading> readings = generator.generate(all, covered, NOW);

        assertThat(readings).hasSize(10_000);
        long coveredOccupied = readings.subList(0, 5000).stream().filter(SensorReading::occupied).count();
        long otherOccupied = readings.subList(5000, 10_000).stream().filter(SensorReading::occupied).count();
        assertThat(coveredOccupied).isBetween(4100L, 4400L);
        assertThat(otherOccupied).isBetween(20L, 90L);
    }

    @Test
    void extremesAreDeterministic() {
        List<SpotSensor> all = spots(50);
        Set<Long> covered = Set.of(1L, 2L, 3L);

        List<SensorReading> none = new SimulatedReadingGenerator(new Random(1), 0.0, 1.0, 1.0)
                .generate(all, covered, NOW);
        List<SensorReading> exact = new SimulatedReadingGenerator(new Random(1), 1.0, 1.0, 0.0)
                .generate(all, covered, NOW);

        assertThat(none).isEmpty();
        List<String> occupied = new ArrayList<>();
        exact.stream().filter(SensorReading::occupied).forEach(r -> occupied.add(r.sensorId()));
        assertThat(occupied).containsExactly("SNS-X-1", "SNS-X-2", "SNS-X-3");
    }

    @Test
    void rejectsInvalidProbabilities() {
        assertThatThrownBy(() -> new SimulatedReadingGenerator(new Random(), 1.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SimulatedReadingGenerator(new Random(), 0.5, Double.NaN, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
