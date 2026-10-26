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

        assertThat(applied).extracting(info -> info.getVersion().getVersion()).containsExactly("1", "2", "3");
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
    void openApiDeclaresBearerJwtScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    void openApiDeclaresSensorKeySchemeAndTagsOperationsApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.sensorKey.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.sensorKey.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.sensorKey.name").value("X-Sensor-Key"))
                .andExpect(jsonPath("$.paths['/api/sensors/readings'].post.security[0].sensorKey").exists())
                .andExpect(jsonPath("$.paths['/api/manager/stats'].get.summary").value("Dashboard statistics"))
                .andExpect(jsonPath("$.paths['/api/notifications'].get.tags[0]").value("Notifications"));
    }

    @Test
    void protectedAndUnlistedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
