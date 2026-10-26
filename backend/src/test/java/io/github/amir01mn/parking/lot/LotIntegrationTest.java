package io.github.amir01mn.parking.lot;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LotIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-03-01T14:00:00Z");

    @Test
    void listsLotsPubliclyWithCounts() throws Exception {
        jdbc.update("update parking_spots set maintenance_required = true where sensor_id = 'SNS-A-001'");
        jdbc.update("update parking_spots set enabled = false where sensor_id = 'SNS-A-002'");
        jdbc.update("update parking_spots set occupied = true where sensor_id in ('SNS-A-003', 'SNS-A-001')");
        jdbc.update("update parking_lots set enabled = false where name like 'Lot C%'");

        mockMvc.perform(get("/api/lots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Lot A — Keele Campus North"))
                .andExpect(jsonPath("$[0].totalSpots").value(40))
                .andExpect(jsonPath("$[0].bookableSpots").value(38))
                .andExpect(jsonPath("$[0].occupiedSpots").value(2))
                .andExpect(jsonPath("$[1].totalSpots").value(60))
                .andExpect(jsonPath("$[1].bookableSpots").value(60))
                .andExpect(jsonPath("$[2].enabled").value(false))
                .andExpect(jsonPath("$[2].totalSpots").value(30))
                .andExpect(jsonPath("$[2].bookableSpots").value(0));
    }

    @Test
    void getsOneLotOr404() throws Exception {
        long lotB = lotId("Lot B");
        mockMvc.perform(get("/api/lots/{id}", lotB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lotB))
                .andExpect(jsonPath("$.name").value("Lot B — Stong Pond"))
                .andExpect(jsonPath("$.totalSpots").value(60));

        mockMvc.perform(get("/api/lots/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Lot 999999 was not found"));
    }

    @Test
    void listsSpotsWithLiveState() throws Exception {
        jdbc.update("update parking_spots set maintenance_required = true, occupied = true "
                + "where sensor_id = 'SNS-A-001'");

        mockMvc.perform(get("/api/lots/{id}/spots", lotId("Lot A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(40))
                .andExpect(jsonPath("$[0].label").value("A-001"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].maintenanceRequired").value(true))
                .andExpect(jsonPath("$[0].occupied").value(true))
                .andExpect(jsonPath("$[0].bookable").value(false))
                .andExpect(jsonPath("$[1].label").value("A-002"))
                .andExpect(jsonPath("$[1].bookable").value(true));
    }

    @Test
    void availabilityExcludesBookedAndUnbookableSpots() throws Exception {
        clock.setInstant(NOW);
        long lotA = lotId("Lot A");
        long a1 = spotId("SNS-A-001");
        long a2 = spotId("SNS-A-002");
        Instant start = NOW.plus(Duration.ofHours(2));
        Instant end = start.plus(Duration.ofHours(2));
        createBookingId(registerAndLogin("booker@example.com"), a1, start, end);
        jdbc.update("update parking_spots set maintenance_required = true where id = ?", a2);

        mockMvc.perform(get("/api/lots/{id}/availability", lotA)
                        .param("start", start.plus(Duration.ofHours(1)).toString())
                        .param("end", end.plus(Duration.ofHours(1)).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(38))
                .andExpect(jsonPath("$.spots[*].id", not(hasItem((int) a1))))
                .andExpect(jsonPath("$.spots[*].id", not(hasItem((int) a2))));

        // back-to-back with the booking: A-001 is free again
        mockMvc.perform(get("/api/lots/{id}/availability", lotA)
                        .param("start", end.toString())
                        .param("end", end.plus(Duration.ofHours(1)).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(39))
                .andExpect(jsonPath("$.spots[0].label").value("A-001"));
    }

    @Test
    void availabilityValidatesTheWindow() throws Exception {
        long lotA = lotId("Lot A");
        mockMvc.perform(get("/api/lots/{id}/availability", lotA)
                        .param("start", NOW.toString())
                        .param("end", NOW.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
        mockMvc.perform(get("/api/lots/{id}/availability", lotA).param("start", NOW.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/lots/{id}/availability", lotA)
                        .param("start", "yesterday").param("end", NOW.toString()))
                .andExpect(status().isBadRequest());
    }
}
