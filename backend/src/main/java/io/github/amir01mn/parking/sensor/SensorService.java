package io.github.amir01mn.parking.sensor;

import io.github.amir01mn.parking.booking.BookingRepository;
import io.github.amir01mn.parking.config.SensorProperties;
import io.github.amir01mn.parking.lot.ParkingSpot;
import io.github.amir01mn.parking.lot.ParkingSpotRepository;
import io.github.amir01mn.parking.sensor.event.UnauthorizedParkingDetected;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies sensor readings to spots (the single entry point for the HTTP endpoint and the demo simulator).
 *
 * <p>Readings are applied in timestamp order through {@link ParkingSpot#applySensorReading}, so stale readings are
 * ignored and manager flags are never touched. When a spot turns occupied (unoccupied -> occupied) and no CONFIRMED
 * booking covers the current time, {@link UnauthorizedParkingDetected} is published. Readings that keep a spot
 * occupied are not transitions, so one continuous occupancy alerts at most once.
 */
@Service
public class SensorService {

    private static final Logger log = LoggerFactory.getLogger(SensorService.class);

    private final ParkingSpotRepository spotRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher events;
    private final SensorProperties properties;
    private final Clock clock;

    public SensorService(ParkingSpotRepository spotRepository, BookingRepository bookingRepository,
                         ApplicationEventPublisher events, SensorProperties properties, Clock clock) {
        this.spotRepository = spotRepository;
        this.bookingRepository = bookingRepository;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public SensorIngestResult ingest(List<SensorReading> readings) {
        Instant now = clock.instant();
        Instant futureLimit = now.plus(properties.maxClockSkew());
        Set<String> sensorIds = readings.stream().map(SensorReading::sensorId).collect(Collectors.toSet());
        Map<String, ParkingSpot> spots = spotRepository.findBySensorIdInForUpdate(sensorIds).stream()
                .collect(Collectors.toMap(ParkingSpot::getSensorId, Function.identity()));

        int accepted = 0;
        int stale = 0;
        int future = 0;
        Set<String> unknown = new TreeSet<>();
        // spots whose occupancy started in this batch and that are still occupied at its end
        Map<Long, ParkingSpot> newlyOccupied = new LinkedHashMap<>();

        List<SensorReading> ordered = readings.stream()
                .sorted(Comparator.comparing(SensorReading::timestamp))
                .toList();
        for (SensorReading reading : ordered) {
            ParkingSpot spot = spots.get(reading.sensorId());
            if (spot == null) {
                unknown.add(reading.sensorId());
                continue;
            }
            if (reading.timestamp().isAfter(futureLimit)) {
                future++;
                continue;
            }
            boolean wasOccupied = spot.isOccupied();
            if (!spot.applySensorReading(reading.occupied(), reading.timestamp())) {
                stale++;
                continue;
            }
            accepted++;
            if (!wasOccupied && spot.isOccupied()) {
                newlyOccupied.put(spot.getId(), spot);
            } else if (!spot.isOccupied()) {
                newlyOccupied.remove(spot.getId());
            }
        }

        raiseUnauthorizedAlerts(newlyOccupied, now);
        if (!unknown.isEmpty()) {
            log.warn("Sensor batch had readings from {} unknown sensor(s): {}", unknown.size(), unknown);
        }
        log.debug("Sensor batch: {} accepted, {} stale, {} future, {} unknown sensors", accepted, stale, future,
                unknown.size());
        return new SensorIngestResult(accepted, stale, future, List.copyOf(unknown));
    }

    private void raiseUnauthorizedAlerts(Map<Long, ParkingSpot> newlyOccupied, Instant now) {
        if (newlyOccupied.isEmpty()) {
            return;
        }
        Set<Long> covered = new HashSet<>(bookingRepository.findSpotIdsCoveredAt(newlyOccupied.keySet(), now));
        for (ParkingSpot spot : newlyOccupied.values()) {
            if (covered.contains(spot.getId())) {
                continue;
            }
            log.info("Unauthorized parking on spot {} ({})", spot.getLabel(), spot.getSensorId());
            events.publishEvent(new UnauthorizedParkingDetected(spot.getId(), spot.getLabel(), spot.getLot().getId(),
                    spot.getLot().getName(), spot.getSensorId(), spot.getOccupiedSince(), now));
        }
    }
}
