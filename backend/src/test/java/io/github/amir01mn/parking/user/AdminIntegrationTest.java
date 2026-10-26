package io.github.amir01mn.parking.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminIntegrationTest extends AbstractApiIntegrationTest {

    private String superManager;

    @BeforeEach
    void setUp() throws Exception {
        superManager = superManagerToken();
    }

    @Test
    void superManagerCreatesManagerWhoseOneTimePasswordWorks() throws Exception {
        JsonNode created = json(postJson("/api/admin/managers",
                Map.of("firstName", " Carlos ", "lastName", "Mendes", "email", "Carlos.Mendes@YorkU.ca"), superManager)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.manager.email").value("carlos.mendes@yorku.ca"))
                .andExpect(jsonPath("$.manager.firstName").value("Carlos"))
                .andExpect(jsonPath("$.manager.role").value("MANAGER"))
                .andExpect(jsonPath("$.manager.userType").value("STAFF")) // from the directory
                .andReturn());
        String password = created.get("temporaryPassword").asText();
        assertThat(PasswordPolicy.violations(password)).isEmpty();
        assertThat(jdbc.queryForObject("select password_hash from users where email = 'carlos.mendes@yorku.ca'",
                String.class)).startsWith("$2").doesNotContain(password);

        String token = login("carlos.mendes@yorku.ca", password);
        getWithToken("/api/manager/stats", token).andExpect(status().isOk());
        getWithToken("/api/admin/managers", token).andExpect(status().isForbidden());
    }

    @Test
    void userTypeComesFromTheDirectoryElseStaff() throws Exception {
        postJson("/api/admin/managers", Map.of("firstName", "M", "lastName", "Wong",
                "email", "margaret.wong@yorku.ca"), superManager)
                .andExpect(jsonPath("$.manager.userType").value("FACULTY"));
        postJson("/api/admin/managers", Map.of("firstName", "Out", "lastName", "Sider",
                "email", "contractor@example.com"), superManager)
                .andExpect(jsonPath("$.manager.userType").value("STAFF"));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        register("taken@example.com", PASSWORD);
        postJson("/api/admin/managers", Map.of("firstName", "A", "lastName", "B", "email", "TAKEN@example.com"),
                superManager)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists."));
        postJson("/api/admin/managers", Map.of("firstName", "A", "lastName", "B", "email", SUPER_EMAIL),
                superManager)
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select role from users where email = 'taken@example.com'", String.class))
                .isEqualTo("USER");
    }

    @Test
    void managersAndUsersCannotAdministerManagers() throws Exception {
        String manager = registerManager("manager@yorku.ca");
        String user = registerAndLogin("user@example.com");
        Map<String, String> body = Map.of("firstName", "A", "lastName", "B", "email", "new@yorku.ca");

        postJson("/api/admin/managers", body, manager).andExpect(status().isForbidden());
        postJson("/api/admin/managers", body, user).andExpect(status().isForbidden());
        getWithToken("/api/admin/managers", manager).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from users where email = 'new@yorku.ca'", Long.class))
                .isZero();
    }

    @Test
    void validatesInputAndListsManagers() throws Exception {
        postJson("/api/admin/managers", Map.of("firstName", "", "lastName", "B", "email", "not-an-email"),
                superManager)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));

        registerManager("zed@yorku.ca");
        postJson("/api/admin/managers", Map.of("firstName", "Ann", "lastName", "Able", "email", "ann@yorku.ca"),
                superManager).andExpect(status().isCreated());
        register("plain@example.com", PASSWORD);

        getWithToken("/api/admin/managers", superManager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].email").value("ann@yorku.ca"))
                .andExpect(jsonPath("$.content[1].email").value("zed@yorku.ca"));
    }
}
