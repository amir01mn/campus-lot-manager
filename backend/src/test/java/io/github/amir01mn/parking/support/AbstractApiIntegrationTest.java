package io.github.amir01mn.parking.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
 * all users except the bootstrapped super manager, manager-created lots), reset spot/lot flags and sensor state, and
 * a clock following system time.
 */
public abstract class AbstractApiIntegrationTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "Secret#123";
    protected static final String SUPER_EMAIL = "super@test.local";
    protected static final String SUPER_PASSWORD = "Test!Passw0rd";
    protected static final String SENSOR_KEY = "test-sensor-key";

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
        // lots created through the manager API have SNS-L<lotId>-NNN sensors; seeded lots keep theirs
        jdbc.execute("delete from parking_spots where sensor_id like 'SNS-L%'");
        jdbc.execute("delete from parking_lots l where not exists (select 1 from parking_spots s where s.lot_id = l.id)");
        jdbc.execute("update parking_spots set enabled = true, maintenance_required = false, occupied = false, "
                + "last_sensor_update = null, occupied_since = null");
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

    protected ResultActions patchJson(String url, Object body, String token) throws Exception {
        return mockMvc.perform(jsonRequest(patch(url), body).with(bearer(token)));
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

    protected String superManagerToken() throws Exception {
        return login(SUPER_EMAIL, SUPER_PASSWORD);
    }

    /** One sensor reading as a JSON-ready map. */
    protected static Map<String, Object> reading(String sensorId, boolean occupied, Instant timestamp) {
        Map<String, Object> reading = new LinkedHashMap<>();
        reading.put("sensorId", sensorId);
        reading.put("occupied", occupied);
        reading.put("timestamp", timestamp.toString());
        return reading;
    }

    /** Posts a batch to the sensor endpoint with the correct key. */
    protected ResultActions postReadings(List<Map<String, Object>> readings) throws Exception {
        return mockMvc.perform(jsonRequest(post("/api/sensors/readings"), Map.of("readings", readings))
                .header("X-Sensor-Key", SENSOR_KEY));
    }

    /** Notification types of the user with {@code email}, newest first. */
    protected List<String> notificationTypes(String email) {
        return jdbc.queryForList("select n.type from notifications n join users u on u.id = n.recipient_id "
                + "where u.email = ? order by n.created_at desc, n.id desc", String.class, email);
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
