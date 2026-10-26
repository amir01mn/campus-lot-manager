package io.github.amir01mn.parking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import io.github.amir01mn.parking.user.SuperManagerBootstrap;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends AbstractApiIntegrationTest {

    private static final String SUPER_EMAIL = "super@test.local";
    private static final String SUPER_PASSWORD = "Test!Passw0rd";

    @Autowired
    private SuperManagerBootstrap superManagerBootstrap;

    @Test
    void registerTakesTypeFromDirectoryAndNormalisesEmail() throws Exception {
        postJson("/api/auth/register", Map.of("firstName", "Daniel", "lastName", "Chen",
                "email", "  Daniel.Chen@My.YorkU.ca ", "password", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/me"))
                .andExpect(jsonPath("$.email").value("daniel.chen@my.yorku.ca"))
                .andExpect(jsonPath("$.userType").value("STUDENT"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.hourlyRate").value(5.0))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerUnknownEmailIsVisitor() throws Exception {
        assertThat(register("someone@gmail.com", PASSWORD).get("userType").asText()).isEqualTo("VISITOR");
        assertThat(register("someone2@gmail.com", PASSWORD).get("hourlyRate").decimalValue())
                .isEqualByComparingTo("15.00");
    }

    @Test
    void weakPasswordIs400WithEveryBrokenRule() throws Exception {
        postJson("/api/auth/register", Map.of("firstName", "A", "lastName", "B", "email", "weak@example.com",
                "password", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("must contain an upper-case letter")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("must contain a digit")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("must contain a special character")));
        assertThat(jdbc.queryForObject("select count(*) from users where email = 'weak@example.com'", Long.class))
                .isZero();
    }

    @Test
    void invalidFieldsAre400() throws Exception {
        postJson("/api/auth/register", Map.of("firstName", "", "lastName", "B", "email", "not-an-email",
                "password", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("firstName")));
    }

    @Test
    void duplicateEmailIs409CaseInsensitively() throws Exception {
        register("dup@example.com", PASSWORD);

        postJson("/api/auth/register", Map.of("firstName", "A", "lastName", "B", "email", "DUP@Example.com",
                "password", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists."));
    }

    @Test
    void loginReturnsBearerTokenThatUnlocksMe() throws Exception {
        register("faculty.demo@yorku.ca", PASSWORD);

        String token = json(postJson("/api/auth/login", Map.of("email", "Faculty.Demo@YorkU.ca",
                "password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(43200)) // PT12H in application-test.yml
                .andExpect(jsonPath("$.user.email").value("faculty.demo@yorku.ca"))
                .andExpect(jsonPath("$.user.userType").value("FACULTY"))
                .andReturn()).get("accessToken").asText();

        getWithToken("/api/users/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("faculty.demo@yorku.ca"))
                .andExpect(jsonPath("$.userType").value("FACULTY"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.hourlyRate").value(8.0))
                .andExpect(jsonPath("$.currency").value("CAD"));
    }

    @Test
    void failedLoginIs401AndChangesNothing() throws Exception {
        register("victim@example.com", PASSWORD);
        Map<String, Object> before = jdbc.queryForMap(
                "select password_hash, version from users where email = 'victim@example.com'");

        postJson("/api/auth/login", Map.of("email", "victim@example.com", "password", "Wrong#Pass1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:campus-parking:problem:unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));

        Map<String, Object> after = jdbc.queryForMap(
                "select password_hash, version from users where email = 'victim@example.com'");
        assertThat(after).isEqualTo(before);
        login("victim@example.com", PASSWORD); // the original password still works
    }

    @Test
    void unknownEmailLoginIs401WithSameMessage() throws Exception {
        postJson("/api/auth/login", Map.of("email", "ghost@example.com", "password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void protectedEndpointWithoutTokenIs401ProblemWithChallenge() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").value("urn:campus-parking:problem:unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/users/me"));
    }

    @Test
    void invalidAndExpiredTokensAre401() throws Exception {
        getWithToken("/api/users/me", "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("The access token is invalid or has expired."));

        String token = registerAndLogin("expiring@example.com");
        getWithToken("/api/users/me", token).andExpect(status().isOk());
        clock.advance(Duration.ofHours(13));
        getWithToken("/api/users/me", token).andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndPolicy() throws Exception {
        String token = registerAndLogin("changer@example.com");

        mockMvc.perform(jsonRequest(put("/api/users/me/password"),
                        Map.of("currentPassword", "Wrong#Pass1", "newPassword", "Better#Pass2"))
                        .with(bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
        mockMvc.perform(jsonRequest(put("/api/users/me/password"),
                        Map.of("currentPassword", PASSWORD, "newPassword", "short"))
                        .with(bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(jsonRequest(put("/api/users/me/password"),
                        Map.of("currentPassword", PASSWORD, "newPassword", "Better#Pass2"))
                        .with(bearer(token)))
                .andExpect(status().isNoContent());

        postJson("/api/auth/login", Map.of("email", "changer@example.com", "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        login("changer@example.com", "Better#Pass2");
    }

    @Test
    void rolesAreHierarchical() throws Exception {
        String userToken = registerAndLogin("plain@example.com");
        getWithToken("/api/manager/stats", userToken)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:campus-parking:problem:forbidden"));

        String managerToken = registerManager("boss@example.com");
        getWithToken("/api/manager/stats", managerToken).andExpect(status().isOk());
        getWithToken("/api/admin/managers", managerToken).andExpect(status().isForbidden());

        String superToken = login(SUPER_EMAIL, SUPER_PASSWORD);
        getWithToken("/api/users/me", superToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_MANAGER"));
        getWithToken("/api/manager/stats", superToken).andExpect(status().isOk());
        getWithToken("/api/admin/managers", superToken).andExpect(status().isOk());
    }

    @Test
    void superManagerBootstrapIsIdempotent() throws Exception {
        superManagerBootstrap.run(null);
        superManagerBootstrap.run(null);

        assertThat(jdbc.queryForObject("select count(*) from users where role = 'SUPER_MANAGER'", Long.class))
                .isEqualTo(1);
        login(SUPER_EMAIL, SUPER_PASSWORD);
    }
}
