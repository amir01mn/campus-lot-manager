package io.github.amir01mn.parking.sensor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.amir01mn.parking.booking.AutoCheckoutJob;
import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Scheduled jobs, called directly (scheduling is off in the test profile). */
class JobsIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-03-01T14:00:00Z");

    @Autowired
    private AutoCheckoutJob autoCheckout;

    @Autowired
    private OverstayDetectionJob overstay;

    private String student;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        student = registerAndLogin("student.demo@my.yorku.ca"); // 5.00/h
    }

    @Test
    void autoCheckoutCompletesEndedBookingsWithFinalPaymentAndSkipsCancelled() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(2));
        String ended = createBookingId(student, spotId("SNS-A-001"), start, start.plus(Duration.ofHours(2)));
        String cancelled = createBookingId(student, spotId("SNS-A-002"), start.plus(Duration.ofHours(3)),
                start.plus(Duration.ofHours(4)));
        postJson("/api/bookings/" + cancelled + "/cancel", Map.of(), student).andExpect(status().isOk());
        String later = createBookingId(student, spotId("SNS-A-003"), start.plus(Duration.ofHours(5)),
                start.plus(Duration.ofHours(7)));

        assertThat(autoCheckout.run()).isZero();

        clock.setInstant(start.plus(Duration.ofHours(2))); // exactly the end of the first booking
        assertThat(autoCheckout.run()).isEqualTo(1);
        assertThat(autoCheckout.run()).as("idempotent").isZero();

        assertThat(bookingStatus(ended)).isEqualTo("COMPLETED");
        assertThat(payments(ended)).containsExactly("DEPOSIT 5.00", "FINAL 5.00");
        assertThat(bookingStatus(cancelled)).isEqualTo("CANCELLED");
        assertThat(payments(cancelled)).containsExactly("DEPOSIT 5.00", "REFUND 5.00");
        assertThat(bookingStatus(later)).isEqualTo("CONFIRMED");
        assertThat(notificationTypes("student.demo@my.yorku.ca")).contains("BOOKING_COMPLETED");
    }

    @Test
    void overstayAlertsOncePerBookingAfterTheGracePeriod() throws Exception {
        registerManager("manager@yorku.ca");
        Instant end = NOW.plus(Duration.ofHours(1));
        String booking = createBookingId(student, spotId("SNS-C-001"), NOW, end);
        postReadings(List.of(reading("SNS-C-001", true, NOW.plusSeconds(30)))).andExpect(status().isOk());
        assertThat(notificationTypes("manager@yorku.ca")).as("covered occupancy is not unauthorized").isEmpty();

        clock.setInstant(end.plus(Duration.ofMinutes(5)));
        autoCheckout.run();
        assertThat(overstay.run()).as("still within the 10 min grace").isZero();

        clock.setInstant(end.plus(Duration.ofMinutes(11)));
        assertThat(overstay.run()).isEqualTo(1);
        clock.advance(Duration.ofMinutes(5));
        assertThat(overstay.run()).as("once per booking").isZero();

        assertThat(notificationTypes("manager@yorku.ca")).containsExactly("OVERSTAY_DETECTED");
        assertThat(notificationTypes(SUPER_EMAIL)).containsExactly("OVERSTAY_DETECTED");
        assertThat(jdbc.queryForObject("select overstay_alerted_at from bookings where id = ?::uuid",
                java.sql.Timestamp.class, booking)).isNotNull();
        assertThat(jdbc.queryForObject("select message from notifications where type = 'OVERSTAY_DETECTED' "
                + "limit 1", String.class)).contains("C-001").contains("ABC 123");
    }

    @Test
    void noOverstayWhenTheCarLeftOrANewCarArrivedAfterTheEnd() throws Exception {
        registerManager("manager@yorku.ca");
        Instant end = NOW.plus(Duration.ofHours(1));
        createBookingId(student, spotId("SNS-C-002"), NOW, end);
        String visitor = registerAndLogin("visitor@example.com");
        createBookingId(visitor, spotId("SNS-C-004"), NOW, end);
        postReadings(List.of(
                reading("SNS-C-002", true, NOW.plusSeconds(10)),
                reading("SNS-C-004", true, NOW.plusSeconds(10)))).andExpect(status().isOk());

        // C-002: the car leaves on time; C-004: it leaves and a different car parks after the end
        clock.setInstant(end.minusSeconds(60));
        postReadings(List.of(
                reading("SNS-C-002", false, end.minusSeconds(60)),
                reading("SNS-C-004", false, end.minusSeconds(60)))).andExpect(status().isOk());
        clock.setInstant(end.plus(Duration.ofMinutes(2)));
        postReadings(List.of(reading("SNS-C-004", true, clock.instant()))).andExpect(status().isOk());

        clock.setInstant(end.plus(Duration.ofMinutes(30)));
        assertThat(overstay.run()).isZero();
        assertThat(notificationTypes("manager@yorku.ca")).containsExactly("UNAUTHORIZED_PARKING");
    }

    private String bookingStatus(String bookingId) {
        return jdbc.queryForObject("select status from bookings where id = ?::uuid", String.class, bookingId);
    }

    private List<String> payments(String bookingId) {
        return jdbc.queryForList("select type || ' ' || amount from payments where booking_id = ?::uuid "
                + "order by created_at, type", String.class, bookingId);
    }
}
