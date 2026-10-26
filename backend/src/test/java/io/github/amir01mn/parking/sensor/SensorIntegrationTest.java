package io.github.amir01mn.parking.sensor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SensorIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-03-01T14:00:00Z");
    private static final String URL = "/api/sensors/readings";

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
    }

    @Test
    void missingOrWrongKeyIs401ProblemDetail() throws Exception {
        Map<String, Object> body = Map.of("readings", List.of(reading("SNS-A-001", true, NOW)));

        mockMvc.perform(jsonRequest(post(URL), body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:campus-parking:problem:unauthorized"))
                .andExpect(jsonPath("$.detail").value("The X-Sensor-Key header is required."));
        mockMvc.perform(jsonRequest(post(URL), body).header("X-Sensor-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("The X-Sensor-Key header is invalid."));
        mockMvc.perform(jsonRequest(post(URL), body).header("X-Sensor-Key", SENSOR_KEY + "x"))
                .andExpect(status().isUnauthorized());

        // a user's JWT is not a sensor credential
        String token = registerAndLogin("someone@example.com");
        mockMvc.perform(jsonRequest(post(URL), body).with(bearer(token))).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select occupied from parking_spots where sensor_id = 'SNS-A-001'",
                Boolean.class)).isFalse();
    }

    @Test
    void appliesReadingsIgnoresStaleAndReportsUnknownSensors() throws Exception {
        postReadings(List.of(
                reading("SNS-A-001", false, NOW.minusSeconds(10)),
                reading("SNS-A-002", false, NOW.minusSeconds(5)),
                reading("SNS-NOPE-1", true, NOW),
                reading("SNS-NOPE-1", false, NOW),
                reading("SNS-NOPE-0", true, NOW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.ignoredStale").value(0))
                .andExpect(jsonPath("$.ignoredFuture").value(0))
                .andExpect(jsonPath("$.unknownSensors.length()").value(2))
                .andExpect(jsonPath("$.unknownSensors[0]").value("SNS-NOPE-0"))
                .andExpect(jsonPath("$.unknownSensors[1]").value("SNS-NOPE-1"));

        // older than the last applied reading -> ignored; too far in the future -> not applied
        postReadings(List.of(
                reading("SNS-A-001", true, NOW.minusSeconds(20)),
                reading("SNS-A-002", true, NOW.plus(Duration.ofMinutes(30)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.ignoredStale").value(1))
                .andExpect(jsonPath("$.ignoredFuture").value(1))
                .andExpect(jsonPath("$.unknownSensors.length()").value(0));

        assertThat(jdbc.queryForList("select occupied from parking_spots where sensor_id in ('SNS-A-001', "
                + "'SNS-A-002')", Boolean.class)).containsOnly(false);
    }

    @Test
    void readingsInOneBatchAreAppliedInTimestampOrder() throws Exception {
        postReadings(List.of(
                reading("SNS-A-003", false, NOW),
                reading("SNS-A-003", true, NOW.minusSeconds(30))))
                .andExpect(jsonPath("$.accepted").value(2));

        assertThat(jdbc.queryForObject("select occupied from parking_spots where sensor_id = 'SNS-A-003'",
                Boolean.class)).isFalse();
    }

    @Test
    void readingsNeverTouchManagerFlags() throws Exception {
        String manager = registerManager("manager@yorku.ca");
        long spot = spotId("SNS-A-004");
        patchJson("/api/manager/spots/" + spot, Map.of("enabled", false, "maintenanceRequired", true), manager)
                .andExpect(status().isOk());

        postReadings(List.of(reading("SNS-A-004", true, NOW), reading("SNS-A-004", false, NOW.plusSeconds(1))))
                .andExpect(jsonPath("$.accepted").value(2));

        Map<String, Object> row = jdbc.queryForMap("select enabled, maintenance_required, occupied, "
                + "last_sensor_update from parking_spots where id = ?", spot);
        assertThat(row.get("enabled")).isEqualTo(false);
        assertThat(row.get("maintenance_required")).isEqualTo(true);
        assertThat(row.get("occupied")).isEqualTo(false);
        assertThat(row.get("last_sensor_update")).isNotNull();
    }

    @Test
    void unauthorizedParkingAlertsOncePerContinuousOccupancy() throws Exception {
        registerManager("manager@yorku.ca");

        postReadings(List.of(reading("SNS-B-010", true, NOW))).andExpect(status().isOk());
        assertThat(notificationTypes("manager@yorku.ca")).containsExactly("UNAUTHORIZED_PARKING");
        assertThat(notificationTypes(SUPER_EMAIL)).containsExactly("UNAUTHORIZED_PARKING");
        assertThat(jdbc.queryForObject("select message from notifications limit 1", String.class))
                .contains("spot B-010").contains("Lot B");

        // still the same car: no new alert
        clock.advance(Duration.ofMinutes(5));
        postReadings(List.of(reading("SNS-B-010", true, clock.instant()))).andExpect(status().isOk());
        postReadings(List.of(reading("SNS-B-010", true, clock.instant()))).andExpect(status().isOk());
        assertThat(notificationTypes("manager@yorku.ca")).hasSize(1);

        // the car leaves and another arrives: a new occupancy, a new alert
        clock.advance(Duration.ofMinutes(5));
        postReadings(List.of(reading("SNS-B-010", false, clock.instant()),
                reading("SNS-B-010", true, clock.instant().plusSeconds(1)))).andExpect(status().isOk());
        assertThat(notificationTypes("manager@yorku.ca")).hasSize(2);
    }

    @Test
    void occupancyCoveredByAConfirmedBookingIsNotAnAlert() throws Exception {
        registerManager("manager@yorku.ca");
        String student = registerAndLogin("student.demo@my.yorku.ca");
        createBookingId(student, spotId("SNS-A-005"), NOW, NOW.plus(Duration.ofHours(2)));

        postReadings(List.of(reading("SNS-A-005", true, NOW.plusSeconds(60)))).andExpect(status().isOk());

        assertThat(notificationTypes("manager@yorku.ca")).isEmpty();
        assertThat(jdbc.queryForObject("select occupied_since from parking_spots where sensor_id = 'SNS-A-005'",
                java.sql.Timestamp.class).toInstant()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void batchSizeIsValidated() throws Exception {
        postReadings(List.of()).andExpect(status().isBadRequest());

        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            tooMany.add(reading("SNS-A-001", true, NOW));
        }
        postReadings(tooMany)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("readings"));

        postReadings(List.of(Map.of("sensorId", "SNS-A-001", "occupied", true)))
                .andExpect(status().isBadRequest());
    }
}
