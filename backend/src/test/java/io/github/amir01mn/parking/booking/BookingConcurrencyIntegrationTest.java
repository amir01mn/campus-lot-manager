package io.github.amir01mn.parking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BookingConcurrencyIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-03-01T14:00:00Z");
    private static final int ROUNDS = 5;

    @Test
    void parallelCreatesForTheSameSpotAndTimeYieldOneSuccessAndOneConflict() throws Exception {
        clock.setInstant(NOW);
        String alice = registerAndLogin("alice.race@example.com");
        String bob = registerAndLogin("bob.race@example.com");
        long spot = spotId("SNS-B-010");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Instant start = NOW.plus(Duration.ofHours(2 + 3L * round));
                Instant end = start.plus(Duration.ofHours(2));
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Integer>> results = new ArrayList<>();
                for (String token : List.of(alice, bob)) {
                    Callable<Integer> create = () -> {
                        go.await();
                        return createBooking(token, spot, start, end).andReturn().getResponse().getStatus();
                    };
                    results.add(executor.submit(create));
                }
                go.countDown();

                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> result : results) {
                    statuses.add(result.get(30, TimeUnit.SECONDS));
                }
                assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(201, 409);
                assertThat(jdbc.queryForObject("select count(*) from bookings where spot_id = ? and start_time = ?",
                        Long.class, spot, Timestamp.from(start))).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
        // only the winners were charged
        assertThat(jdbc.queryForObject("select count(*) from payments", Long.class)).isEqualTo(ROUNDS);
    }
}
