package io.github.amir01mn.parking.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.amir01mn.parking.booking.event.BookingConfirmed;
import io.github.amir01mn.parking.lot.event.MaintenanceFlagged;
import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class NotificationListenerIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-03-01T14:00:00Z");

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        tx = new TransactionTemplate(transactionManager);
    }

    @Test
    void bookingLifecycleNotifiesTheOwnerAfterCommit() throws Exception {
        String student = registerAndLogin("student.demo@my.yorku.ca");
        Instant start = NOW.plus(Duration.ofHours(2));
        String id = createBookingId(student, spotId("SNS-A-001"), start, start.plus(Duration.ofHours(2)));
        postJson("/api/bookings/" + id + "/extend", Map.of("newEnd",
                start.plus(Duration.ofHours(3)).toString()), student).andExpect(status().isOk());
        postJson("/api/bookings/" + id + "/cancel", Map.of(), student).andExpect(status().isOk());

        assertThat(notificationTypes("student.demo@my.yorku.ca"))
                .containsExactlyInAnyOrder("BOOKING_CONFIRMED", "BOOKING_EXTENDED", "BOOKING_CANCELLED");
        String message = jdbc.queryForObject("select message from notifications where type = 'BOOKING_CANCELLED'",
                String.class);
        assertThat(message).contains("spot A-001 in Lot A").contains("deposit of $5.00 has been refunded");
        assertThat(jdbc.queryForObject("select count(*) from notifications where booking_id = ?::uuid",
                Long.class, id)).isEqualTo(3);
    }

    @Test
    void committedEventCreatesNotificationOnlyAfterCommit() {
        tx.executeWithoutResult(status -> {
            publisher.publishEvent(new MaintenanceFlagged(spotId("SNS-A-001"), "A-001", 1L, "Lot A",
                    UUID.randomUUID(), 0, NOW));
            assertThat(jdbc.queryForObject("select count(*) from notifications", Long.class)).isZero();
        });

        assertThat(notificationTypes(SUPER_EMAIL)).containsExactly("MAINTENANCE_FLAGGED");
    }

    @Test
    void rolledBackTransactionCreatesNoNotification() {
        UUID userId = userId(registerQuietly("rolledback@example.com"));
        registerManagerQuietly("manager@yorku.ca");

        tx.executeWithoutResult(status -> {
            publisher.publishEvent(confirmed(userId));
            publisher.publishEvent(new MaintenanceFlagged(spotId("SNS-A-001"), "A-001", 1L, "Lot A", userId, 0,
                    NOW));
            // inside the transaction nothing is written yet
            assertThat(jdbc.queryForObject("select count(*) from notifications", Long.class)).isZero();
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("select count(*) from notifications", Long.class)).isZero();
    }

    @Test
    void alertEventsNotifyEveryManagerAndSuperManagerOnly() {
        registerQuietly("customer@example.com");
        registerManagerQuietly("manager@yorku.ca");

        tx.executeWithoutResult(status -> publisher.publishEvent(new MaintenanceFlagged(spotId("SNS-B-007"),
                "B-007", 2L, "Lot B", UUID.randomUUID(), 2, NOW)));

        assertThat(notificationTypes("manager@yorku.ca")).containsExactly("MAINTENANCE_FLAGGED");
        assertThat(notificationTypes(SUPER_EMAIL)).containsExactly("MAINTENANCE_FLAGGED");
        assertThat(notificationTypes("customer@example.com")).isEmpty();
        assertThat(jdbc.queryForObject("select message from notifications limit 1", String.class))
                .contains("B-007").contains("2 upcoming confirmed booking(s)");
    }

    private BookingConfirmed confirmed(UUID userId) {
        return new BookingConfirmed(UUID.randomUUID(), "CP-TEST01", userId, spotId("SNS-A-001"), NOW,
                NOW.plus(Duration.ofHours(1)), new BigDecimal("5.00"), new BigDecimal("5.00"), NOW);
    }

    private String registerQuietly(String email) {
        try {
            register(email, PASSWORD);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return email;
    }

    private void registerManagerQuietly(String email) {
        registerQuietly(email);
        jdbc.update("update users set role = 'MANAGER' where email = ?", email);
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
    }
}
