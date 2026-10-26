package io.github.amir01mn.parking.sensor;

import io.github.amir01mn.parking.booking.Booking;
import io.github.amir01mn.parking.booking.BookingRepository;
import io.github.amir01mn.parking.config.JobsProperties;
import io.github.amir01mn.parking.lot.ParkingSpot;
import io.github.amir01mn.parking.sensor.event.OverstayDetected;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes {@link OverstayDetected} for a spot still occupied (by the occupancy that began before the booking
 * ended) more than {@code app.jobs.overstay.grace} after its booking ended. The booking's
 * {@code overstay_alerted_at} is set in the same transaction, so each booking alerts at most once.
 */
@Component
public class OverstayDetectionJob {

    private static final Logger log = LoggerFactory.getLogger(OverstayDetectionJob.class);

    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher events;
    private final JobsProperties.Overstay settings;
    private final Clock clock;

    public OverstayDetectionJob(BookingRepository bookingRepository, ApplicationEventPublisher events,
                                JobsProperties properties, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.events = events;
        this.settings = properties.overstay();
        this.clock = clock;
    }

    /** @return how many overstay alerts this run raised */
    @Scheduled(fixedDelayString = "${app.jobs.overstay.interval:PT1M}",
            initialDelayString = "${app.jobs.overstay.interval:PT1M}")
    @Transactional
    public int run() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(settings.grace());
        List<Booking> candidates = bookingRepository.findOverstayCandidates(cutoff.minus(settings.lookback()),
                cutoff, now);
        for (Booking booking : candidates) {
            booking.markOverstayAlerted(now);
            ParkingSpot spot = booking.getSpot();
            events.publishEvent(new OverstayDetected(booking.getId(), booking.getReferenceCode(),
                    booking.getUser().getId(), booking.getLicencePlate(), spot.getId(), spot.getLabel(),
                    spot.getLot().getName(), booking.getEndTime(), now));
            log.info("Overstay on spot {} after booking {}", spot.getLabel(), booking.getReferenceCode());
        }
        return candidates.size();
    }
}
