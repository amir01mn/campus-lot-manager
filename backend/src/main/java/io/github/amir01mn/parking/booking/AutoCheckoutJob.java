package io.github.amir01mn.parking.booking;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Completes CONFIRMED bookings whose end has passed (FINAL charge, status COMPLETED, {@code BookingCompleted}).
 * Each booking is completed in its own transaction, so one failure does not block the others; it is logged and
 * retried on the next run.
 */
@Component
public class AutoCheckoutJob {

    private static final Logger log = LoggerFactory.getLogger(AutoCheckoutJob.class);

    private final BookingRepository bookingRepository;
    private final BookingCompletionService completionService;
    private final Clock clock;

    public AutoCheckoutJob(BookingRepository bookingRepository, BookingCompletionService completionService,
                           Clock clock) {
        this.bookingRepository = bookingRepository;
        this.completionService = completionService;
        this.clock = clock;
    }

    /** @return how many bookings this run completed */
    @Scheduled(fixedDelayString = "${app.jobs.auto-checkout.interval:PT1M}",
            initialDelayString = "${app.jobs.auto-checkout.interval:PT1M}")
    public int run() {
        List<UUID> ended = bookingRepository
                .findByStatusAndEndTimeLessThanEqual(BookingStatus.CONFIRMED, clock.instant()).stream()
                .map(Booking::getId)
                .toList();
        int completed = 0;
        for (UUID id : ended) {
            try {
                if (completionService.completeIfConfirmed(id)) {
                    completed++;
                }
            } catch (RuntimeException ex) {
                log.error("Auto-checkout of booking {} failed; will retry on the next run", id, ex);
            }
        }
        if (completed > 0) {
            log.info("Auto-checkout completed {} of {} ended booking(s)", completed, ended.size());
        }
        return completed;
    }
}
