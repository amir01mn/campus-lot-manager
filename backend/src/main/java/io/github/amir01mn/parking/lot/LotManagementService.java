package io.github.amir01mn.parking.lot;

import io.github.amir01mn.parking.booking.BookingRepository;
import io.github.amir01mn.parking.common.ConflictException;
import io.github.amir01mn.parking.common.NotFoundException;
import io.github.amir01mn.parking.lot.event.MaintenanceFlagged;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager operations on lots and spots.
 *
 * <p>Disabling a lot or spot, or flagging a spot for maintenance, makes it unbookable for new bookings and extensions
 * but never cancels existing CONFIRMED bookings: the response reports how many upcoming bookings are affected so the
 * manager can follow up (e.g. cancel them, which refunds per the normal rules).
 *
 * <p>Spots of manager-created lots are labelled {@code L<lotId>-NNN} with sensor ids {@code SNS-L<lotId>-NNN}. The lot
 * id makes both unique without any name-derived prefix, and cannot collide with the seeded {@code SNS-A-NNN} style.
 */
@Service
public class LotManagementService {

    private static final Logger log = LoggerFactory.getLogger(LotManagementService.class);

    private final ParkingLotRepository lotRepository;
    private final ParkingSpotRepository spotRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public LotManagementService(ParkingLotRepository lotRepository, ParkingSpotRepository spotRepository,
                                BookingRepository bookingRepository, ApplicationEventPublisher events, Clock clock) {
        this.lotRepository = lotRepository;
        this.spotRepository = spotRepository;
        this.bookingRepository = bookingRepository;
        this.events = events;
        this.clock = clock;
    }

    static String spotLabel(long lotId, int number) {
        return "L%d-%03d".formatted(lotId, number);
    }

    static String sensorId(long lotId, int number) {
        return "SNS-L%d-%03d".formatted(lotId, number);
    }

    /** Creates an enabled lot with {@code capacity} enabled spots. */
    @Transactional
    public LotResponse createLot(CreateLotRequest request) {
        String name = request.name().trim();
        if (lotRepository.existsByName(name)) {
            throw new ConflictException("A lot named \"" + name + "\" already exists.");
        }
        ParkingLot lot = lotRepository.saveAndFlush(new ParkingLot(name, request.location().trim(), clock.instant()));
        int capacity = request.capacityOrDefault();
        List<ParkingSpot> spots = new ArrayList<>(capacity);
        for (int n = 1; n <= capacity; n++) {
            spots.add(new ParkingSpot(lot, spotLabel(lot.getId(), n), sensorId(lot.getId(), n)));
        }
        spotRepository.saveAll(spots);
        log.info("Created lot {} \"{}\" with {} spots", lot.getId(), name, capacity);
        return LotResponse.of(lot, new LotSpotCounts(lot.getId(), (long) capacity, (long) capacity, 0L));
    }

    @Transactional
    public LotUpdateResponse updateLot(Long lotId, UpdateLotRequest request) {
        ParkingLot lot = lotRepository.findById(lotId).orElseThrow(() -> NotFoundException.of("Lot", lotId));
        if (request.name() != null) {
            String name = request.name().trim();
            if (!name.equals(lot.getName()) && lotRepository.existsByName(name)) {
                throw new ConflictException("A lot named \"" + name + "\" already exists.");
            }
            lot.rename(name);
        }
        if (request.location() != null) {
            lot.relocate(request.location().trim());
        }
        if (request.enabled() != null && request.enabled() != lot.isEnabled()) {
            lot.setEnabled(request.enabled());
            log.info("Lot {} {}", lot.getId(), request.enabled() ? "enabled" : "disabled");
        }
        lotRepository.flush();
        long affected = lot.isEnabled() ? 0 : bookingRepository.countUpcomingConfirmedForLot(lotId, clock.instant());
        LotSpotCounts counts = spotRepository.countSpotsForLot(lotId).orElse(LotSpotCounts.empty(lotId));
        return new LotUpdateResponse(LotResponse.of(lot, counts), affected);
    }

    /** Updates manager flags; turning maintenance on publishes {@link MaintenanceFlagged}. */
    @Transactional
    public SpotUpdateResponse updateSpot(UUID managerId, Long spotId, UpdateSpotRequest request) {
        // same lock as bookings and sensor batches, so the version check cannot fail against them
        ParkingSpot spot = spotRepository.findByIdForUpdate(spotId)
                .orElseThrow(() -> NotFoundException.of("Spot", spotId));
        Instant now = clock.instant();
        if (request.enabled() != null) {
            spot.setEnabled(request.enabled());
        }
        boolean newlyFlagged = Boolean.TRUE.equals(request.maintenanceRequired()) && !spot.isMaintenanceRequired();
        if (request.maintenanceRequired() != null) {
            spot.setMaintenanceRequired(request.maintenanceRequired());
        }
        spotRepository.flush();
        // a flagged spot is never bookable, so the count is always available for the event
        long affected = spot.isBookable() ? 0 : bookingRepository.countUpcomingConfirmedForSpot(spotId, now);
        if (newlyFlagged) {
            events.publishEvent(new MaintenanceFlagged(spot.getId(), spot.getLabel(), spot.getLot().getId(),
                    spot.getLot().getName(), managerId, affected, now));
            log.info("Spot {} flagged for maintenance by {}", spot.getLabel(), managerId);
        }
        return new SpotUpdateResponse(SpotResponse.of(spot), affected);
    }
}
