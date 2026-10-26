package io.github.amir01mn.parking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.amir01mn.parking.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class ApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private Clock clock;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void flywayAppliesAllMigrations() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).extracting(info -> info.getVersion().getVersion()).containsExactly("1", "2");
        assertThat(Arrays.stream(applied).map(MigrationInfo::getState))
                .allMatch(state -> state == MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void clockUsesCampusTimeZone() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Toronto"));
    }

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Campus Parking API"));
    }

    @Test
    void everythingElseIsDeniedUntilJwtSecurityExists() throws Exception {
        mockMvc.perform(get("/api/lots")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
    }
}
