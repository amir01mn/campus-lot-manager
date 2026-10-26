package io.github.amir01mn.parking.sensor;

import io.github.amir01mn.parking.booking.BookingRepository;
import io.github.amir01mn.parking.config.SensorProperties;
import io.github.amir01mn.parking.lot.ParkingSpotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code demo} profile only: every {@code app.sensors.simulator.interval} it generates plausible readings for a random
 * subset of spots and feeds them through {@link SensorService}, exactly like the HTTP endpoint does.
 */
@Component
@Profile("demo")
@ConditionalOnProperty(prefix = "app.sensors.simulator", name = "enabled", havingValue = "true")
public class SensorSimulator {

    private static final Logger log = LoggerFactory.getLogger(SensorSimulator.class);

    private final SensorService sensorService;
    private final ParkingSpotRepository spotRepository;
    private final BookingRepository bookingRepository;
    private final SimulatedReadingGenerator generator;
    private final Clock clock;

    public SensorSimulator(SensorService sensorService, ParkingSpotRepository spotRepository,
                           BookingRepository bookingRepository, SensorProperties properties, Clock clock) {
        this.sensorService = sensorService;
        this.spotRepository = spotRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        SensorProperties.Simulator settings = properties.simulator();
        RandomGenerator random = RandomGenerator.getDefault(); // used only from the scheduler thread
        this.generator = new SimulatedReadingGenerator(random, settings.sampleFraction(),
                settings.coveredOccupancy(), settings.uncoveredOccupancy());
        log.info("Sensor simulator enabled (every {})", settings.interval());
    }

    @Scheduled(fixedDelayString = "${app.sensors.simulator.interval:PT30S}", initialDelayString = "PT5S")
    public void tick() {
        Instant now = clock.instant();
        List<SensorReading> readings = generator.generate(spotRepository.findAllSensors(),
                new HashSet<>(bookingRepository.findAllSpotIdsCoveredAt(now)), now);
        if (readings.isEmpty()) {
            return;
        }
        try {
            SensorIngestResult result = sensorService.ingest(readings);
            log.debug("Simulator sent {} reading(s): {}", readings.size(), result);
        } catch (RuntimeException ex) {
            log.warn("Simulated sensor batch failed; retrying on the next tick", ex);
        }
    }
}
