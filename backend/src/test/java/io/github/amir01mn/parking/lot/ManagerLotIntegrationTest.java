package io.github.amir01mn.parking.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManagerLotIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-03-01T14:00:00Z");

    private String manager;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        manager = registerManager("manager@yorku.ca");
    }

    @Test
    void managerEndpointsRequireTheManagerRole() throws Exception {
        String user = registerAndLogin("user@example.com");
        long spot = spotId("SNS-A-001");
        long lot = lotId("Lot A");

        postJson("/api/manager/lots", Map.of("name", "X", "location", "Y"), user).andExpect(status().isForbidden());
        patchJson("/api/manager/lots/" + lot, Map.of("enabled", false), user).andExpect(status().isForbidden());
        patchJson("/api/manager/spots/" + spot, Map.of("enabled", false), user).andExpect(status().isForbidden());
        getWithToken("/api/manager/bookings", user).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:campus-parking:problem:forbidden"));
        getWithToken("/api/manager/stats", user).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/manager/stats")).andExpect(status().isUnauthorized());

        // super managers inherit the manager role
        getWithToken("/api/manager/stats", superManagerToken()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select enabled from parking_spots where id = ?", Boolean.class, spot)).isTrue();
    }

    @Test
    void createLotGeneratesSpotsWithUniqueLabelsAndSensors() throws Exception {
        long id = json(postJson("/api/manager/lots",
                Map.of("name", "Lot D — Pond Road", "location", "Pond Rd", "capacity", 3), manager)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Lot D — Pond Road"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.totalSpots").value(3))
                .andExpect(jsonPath("$.bookableSpots").value(3))
                .andReturn()).get("id").asLong();

        assertThat(jdbc.queryForList("select label || ' ' || sensor_id from parking_spots where lot_id = ? "
                + "order by label", String.class, id)).containsExactly(
                "L%d-001 SNS-L%d-001".formatted(id, id),
                "L%d-002 SNS-L%d-002".formatted(id, id),
                "L%d-003 SNS-L%d-003".formatted(id, id));
        mockMvc.perform(get("/api/lots/{id}/spots", id))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].label").value("L" + id + "-001"));

        long defaultLot = json(postJson("/api/manager/lots", Map.of("name", "Lot E", "location", "Somewhere"),
                manager).andExpect(status().isCreated()).andExpect(jsonPath("$.totalSpots").value(100))
                .andReturn()).get("id").asLong();
        assertThat(defaultLot).isNotEqualTo(id);
        assertThat(jdbc.queryForObject("select count(*) = count(distinct sensor_id) from parking_spots",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from parking_spots where lot_id = ?", Long.class, defaultLot))
                .isEqualTo(100);
        // seeded lots keep their labels
        assertThat(jdbc.queryForObject("select label from parking_spots where sensor_id = 'SNS-A-001'",
                String.class)).isEqualTo("A-001");
    }

    @Test
    void createLotValidatesInput() throws Exception {
        postJson("/api/manager/lots", Map.of("name", "Lot A — Keele Campus North", "location", "x"), manager)
                .andExpect(status().isConflict());
        postJson("/api/manager/lots", Map.of("name", "Big", "location", "x", "capacity", 501), manager)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("capacity"));
        postJson("/api/manager/lots", Map.of("name", "Tiny", "location", "x", "capacity", 0), manager)
                .andExpect(status().isBadRequest());
        postJson("/api/manager/lots", Map.of("name", " ", "location", "x"), manager)
                .andExpect(status().isBadRequest());
    }

    @Test
    void disablingALotMakesItUnbookableButKeepsBookingsAndSpotFlags() throws Exception {
        long lot = lotId("Lot A");
        String student = registerAndLogin("student.demo@my.yorku.ca");
        Instant start = NOW.plus(Duration.ofHours(2));
        createBookingId(student, spotId("SNS-A-001"), start, start.plus(Duration.ofHours(1)));

        patchJson("/api/manager/lots/" + lot, Map.of("enabled", false), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lot.enabled").value(false))
                .andExpect(jsonPath("$.lot.bookableSpots").value(0))
                .andExpect(jsonPath("$.lot.totalSpots").value(40))
                .andExpect(jsonPath("$.affectedUpcomingBookings").value(1));

        mockMvc.perform(get("/api/lots/{id}/availability", lot)
                        .param("start", start.toString()).param("end", start.plus(Duration.ofHours(1)).toString()))
                .andExpect(jsonPath("$.availableCount").value(0));
        assertThat(jdbc.queryForObject("select count(*) from parking_spots s join parking_lots l on l.id = s.lot_id "
                + "where l.id = ? and s.enabled", Long.class, lot)).isEqualTo(40);
        assertThat(jdbc.queryForObject("select count(*) from bookings where status = 'CONFIRMED'", Long.class))
                .isEqualTo(1);

        patchJson("/api/manager/lots/" + lot, Map.of("enabled", true), manager)
                .andExpect(jsonPath("$.lot.bookableSpots").value(40))
                .andExpect(jsonPath("$.affectedUpcomingBookings").value(0));
    }

    @Test
    void renameAndRelocateLot() throws Exception {
        long id = json(postJson("/api/manager/lots", Map.of("name", "Temp", "location", "A", "capacity", 1),
                manager).andReturn()).get("id").asLong();

        patchJson("/api/manager/lots/" + id, Map.of("name", " Renamed ", "location", "B"), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lot.name").value("Renamed"))
                .andExpect(jsonPath("$.lot.location").value("B"))
                .andExpect(jsonPath("$.lot.enabled").value(true));
        patchJson("/api/manager/lots/" + id, Map.of("name", "Lot B — Stong Pond"), manager)
                .andExpect(status().isConflict());
        patchJson("/api/manager/lots/" + id, Map.of("name", "  "), manager).andExpect(status().isBadRequest());
        patchJson("/api/manager/lots/999999", Map.of("enabled", true), manager).andExpect(status().isNotFound());
    }

    @Test
    void disablingASpotRemovesItFromAvailability() throws Exception {
        long lot = lotId("Lot B");
        long spot = spotId("SNS-B-001");
        Instant start = NOW.plus(Duration.ofHours(1));
        String url = "/api/lots/" + lot + "/availability?start=" + start + "&end=" + start.plus(Duration.ofHours(1));

        patchJson("/api/manager/spots/" + spot, Map.of("enabled", false), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spot.enabled").value(false))
                .andExpect(jsonPath("$.spot.bookable").value(false))
                .andExpect(jsonPath("$.affectedUpcomingBookings").value(0));
        mockMvc.perform(get(url))
                .andExpect(jsonPath("$.availableCount").value(59))
                .andExpect(jsonPath("$.spots[*].id", not(hasItem((int) spot))));

        patchJson("/api/manager/spots/" + spot, Map.of("enabled", true), manager)
                .andExpect(jsonPath("$.spot.bookable").value(true));
        mockMvc.perform(get(url)).andExpect(jsonPath("$.availableCount").value(60));
        assertThat(notificationTypes("manager@yorku.ca")).isEmpty();
    }

    @Test
    void flaggingMaintenancePublishesAnAlertOnceAndReportsAffectedBookings() throws Exception {
        long spot = spotId("SNS-B-002");
        String student = registerAndLogin("student.demo@my.yorku.ca");
        Instant start = NOW.plus(Duration.ofHours(2));
        createBookingId(student, spot, start, start.plus(Duration.ofHours(1)));

        patchJson("/api/manager/spots/" + spot, Map.of("maintenanceRequired", true), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spot.maintenanceRequired").value(true))
                .andExpect(jsonPath("$.spot.enabled").value(true))
                .andExpect(jsonPath("$.spot.bookable").value(false))
                .andExpect(jsonPath("$.affectedUpcomingBookings").value(1));
        // already flagged: no second alert
        patchJson("/api/manager/spots/" + spot, Map.of("maintenanceRequired", true), manager)
                .andExpect(status().isOk());

        assertThat(notificationTypes("manager@yorku.ca")).containsExactly("MAINTENANCE_FLAGGED");
        assertThat(notificationTypes(SUPER_EMAIL)).containsExactly("MAINTENANCE_FLAGGED");
        assertThat(notificationTypes("student.demo@my.yorku.ca")).containsExactly("BOOKING_CONFIRMED");
        assertThat(jdbc.queryForObject("select message from notifications where type = 'MAINTENANCE_FLAGGED' "
                + "limit 1", String.class)).contains("B-002").contains("1 upcoming confirmed booking(s)");

        Map<String, Object> clear = new HashMap<>();
        clear.put("maintenanceRequired", false);
        clear.put("enabled", null);
        patchJson("/api/manager/spots/" + spot, clear, manager)
                .andExpect(jsonPath("$.spot.bookable").value(true));
        assertThat(jdbc.queryForObject("select count(*) from bookings where status = 'CONFIRMED'", Long.class))
                .isEqualTo(1);
        patchJson("/api/manager/spots/999999", Map.of("enabled", true), manager).andExpect(status().isNotFound());
    }
}
