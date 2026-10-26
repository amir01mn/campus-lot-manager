package io.github.amir01mn.parking.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationApiIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant T0 = Instant.parse("2030-03-01T14:00:00Z");

    private String alice;
    private String bob;
    private UUID aliceOld;
    private UUID aliceNew;
    private UUID bobOnly;

    @BeforeEach
    void setUp() throws Exception {
        alice = registerAndLogin("alice@example.com");
        bob = registerAndLogin("bob@example.com");
        aliceOld = insert("alice@example.com", "BOOKING_CONFIRMED", "old", T0);
        aliceNew = insert("alice@example.com", "BOOKING_COMPLETED", "new", T0.plusSeconds(60));
        insert("alice@example.com", "BOOKING_CANCELLED", "read already", T0.plusSeconds(30));
        jdbc.update("update notifications set is_read = true where message = 'read already'");
        bobOnly = insert("bob@example.com", "BOOKING_CONFIRMED", "bob's", T0);
    }

    @Test
    void listsOnlyMyNotificationsNewestFirst() throws Exception {
        getWithToken("/api/notifications", alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(aliceNew.toString()))
                .andExpect(jsonPath("$.content[0].type").value("BOOKING_COMPLETED"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[1].message").value("read already"))
                .andExpect(jsonPath("$.content[2].id").value(aliceOld.toString()));

        getWithToken("/api/notifications?unreadOnly=true&size=1", alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(aliceNew.toString()));

        getWithToken("/api/notifications", bob)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(bobOnly.toString()));
    }

    @Test
    void unreadCountAndMarkRead() throws Exception {
        getWithToken("/api/notifications/unread-count", alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        postJson("/api/notifications/" + aliceOld + "/read", Map.of(), alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceOld.toString()))
                .andExpect(jsonPath("$.read").value(true));
        // idempotent
        postJson("/api/notifications/" + aliceOld + "/read", Map.of(), alice).andExpect(status().isOk());

        getWithToken("/api/notifications/unread-count", alice).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void cannotReadSomeoneElsesNotification() throws Exception {
        postJson("/api/notifications/" + bobOnly + "/read", Map.of(), alice)
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select is_read from notifications where id = ?", Boolean.class, bobOnly))
                .isFalse();
    }

    @Test
    void readAllOnlyTouchesMine() throws Exception {
        postJson("/api/notifications/read-all", Map.of(), alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        getWithToken("/api/notifications/unread-count", alice).andExpect(jsonPath("$.count").value(0));
        getWithToken("/api/notifications/unread-count", bob).andExpect(jsonPath("$.count").value(1));
        postJson("/api/notifications/read-all", Map.of(), alice).andExpect(jsonPath("$.updated").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    private UUID insert(String email, String type, String message, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into notifications (id, recipient_id, type, message, is_read, created_at) "
                        + "select ?, u.id, ?, ?, false, ? from users u where u.email = ?",
                id, type, message, Timestamp.from(createdAt), email);
        return id;
    }
}
