package io.github.amir01mn.parking.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * HTTP-level integration tests. Every test starts and ends with clean tables (bookings, payments, notifications,
 * all users except the bootstrapped super manager), reset spot/lot flags and a clock following system time.
 */
public abstract class AbstractApiIntegrationTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "Secret#123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    @AfterEach
    void resetState() {
        clock.reset();
        jdbc.execute("delete from notifications");
        jdbc.execute("delete from payments");
        jdbc.execute("delete from bookings");
        jdbc.execute("delete from users where role <> 'SUPER_MANAGER'");
        jdbc.execute("update parking_spots set enabled = true, maintenance_required = false, occupied = false, "
                + "last_sensor_update = null");
        jdbc.execute("update parking_lots set enabled = true");
    }

    protected static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(jsonRequest(post(url), body));
    }

    protected ResultActions postJson(String url, Object body, String token) throws Exception {
        return mockMvc.perform(jsonRequest(post(url), body).with(bearer(token)));
    }

    protected ResultActions getWithToken(String url, String token) throws Exception {
        return mockMvc.perform(get(url).with(bearer(token)));
    }

    protected MockHttpServletRequestBuilder jsonRequest(MockHttpServletRequestBuilder builder, Object body)
            throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected JsonNode register(String email, String password) throws Exception {
        Map<String, Object> body = Map.of("firstName", "Test", "lastName", "User", "email", email,
                "password", password);
        return json(postJson("/api/auth/register", body).andExpect(status().isCreated()).andReturn());
    }

    protected String login(String email, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", Map.of("email", email, "password", password))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asText();
    }

    /** Registers {@code email} with {@link #PASSWORD} and returns an access token. */
    protected String registerAndLogin(String email) throws Exception {
        register(email, PASSWORD);
        return login(email, PASSWORD);
    }

    /** Registers a user, promotes it to MANAGER directly in the database and returns a fresh token. */
    protected String registerManager(String email) throws Exception {
        register(email, PASSWORD);
        jdbc.update("update users set role = 'MANAGER' where email = ?", email);
        return login(email, PASSWORD);
    }

    protected long spotId(String sensorId) {
        Long id = jdbc.queryForObject("select id from parking_spots where sensor_id = ?", Long.class, sensorId);
        if (id == null) {
            throw new IllegalStateException("No spot " + sensorId);
        }
        return id;
    }

    protected long lotId(String namePrefix) {
        Long id = jdbc.queryForObject("select id from parking_lots where name like ?", Long.class, namePrefix + "%");
        if (id == null) {
            throw new IllegalStateException("No lot " + namePrefix);
        }
        return id;
    }

    protected static Map<String, Object> bookingBody(long spotId, String plate, Instant start, Instant end) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("spotId", spotId);
        body.put("plate", plate);
        body.put("start", start.toString());
        body.put("end", end.toString());
        body.put("paymentMethod", "CREDIT_CARD");
        return body;
    }

    protected ResultActions createBooking(String token, long spotId, Instant start, Instant end) throws Exception {
        return postJson("/api/bookings", bookingBody(spotId, "ABC 123", start, end), token);
    }

    /** Creates a booking expecting 201 and returns its id. */
    protected String createBookingId(String token, long spotId, Instant start, Instant end) throws Exception {
        return json(createBooking(token, spotId, start, end).andExpect(status().isCreated()).andReturn())
                .get("id").asText();
    }
}
