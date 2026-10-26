package io.github.amir01mn.parking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingIntegrationTest extends AbstractApiIntegrationTest {

    /** 09:00 in Toronto. */
    private static final Instant NOW = Instant.parse("2030-03-01T14:00:00Z");

    private String student;
    private long spotA1;
    private long spotA2;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        student = registerAndLogin("student.demo@my.yorku.ca"); // STUDENT: 5.00/h
        spotA1 = spotId("SNS-A-001");
        spotA2 = spotId("SNS-A-002");
    }

    @Test
    void createConfirmsBookingAndRecordsDeposit() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(2));
        String id = json(postJson("/api/bookings",
                bookingBody(spotA1, " abc-12 ", start, start.plus(Duration.ofMinutes(150))), student)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/bookings/")))
                .andExpect(jsonPath("$.referenceCode", matchesPattern("^CP-[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.licencePlate").value("ABC-12"))
                .andExpect(jsonPath("$.spot.label").value("A-001"))
                .andExpect(jsonPath("$.spot.lotName").value("Lot A — Keele Campus North"))
                .andExpect(jsonPath("$.hourlyRate").value(5.0))
                .andExpect(jsonPath("$.depositAmount").value(5.0))
                .andExpect(jsonPath("$.totalAmount").value(15.0))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()))
                .andReturn()).get("id").asText();

        assertThat(payments(id)).containsExactly("DEPOSIT 5.00 SUCCEEDED");
        assertThat(jdbc.queryForObject("select provider_reference from payments where booking_id = ?::uuid",
                String.class, id)).startsWith("SIM-");
        getWithToken("/api/bookings/" + id, student).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void rateIsSnapshottedFromTheUserTier() throws Exception {
        String visitor = registerAndLogin("visitor@example.com");
        Instant start = NOW.plus(Duration.ofHours(1));
        createBooking(visitor, spotA2, start, start.plus(Duration.ofMinutes(30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hourlyRate").value(15.0))
                .andExpect(jsonPath("$.totalAmount").value(15.0));
    }

    @Test
    void overlappingBookingOnSameSpotIs409() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(2));
        createBookingId(student, spotA1, start, start.plus(Duration.ofHours(2)));
        String other = registerAndLogin("other@example.com");

        createBooking(other, spotA1, start.plus(Duration.ofHours(1)), start.plus(Duration.ofHours(3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Spot A-001 is already booked")));
        // back-to-back is fine
        createBooking(other, spotA1, start.plus(Duration.ofHours(2)), start.plus(Duration.ofHours(3)))
                .andExpect(status().isCreated());
    }

    @Test
    void userCannotHoldTwoOverlappingBookings() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(2));
        String first = createBookingId(student, spotA1, start, start.plus(Duration.ofHours(2)));
        String reference = jdbc.queryForObject("select reference_code from bookings where id = ?::uuid",
                String.class, first);

        createBooking(student, spotA2, start.plus(Duration.ofMinutes(30)), start.plus(Duration.ofHours(3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "You already have a confirmed booking (" + reference + ") that overlaps this time."));
    }

    @Test
    void unbookableSpotIs422() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(2));
        jdbc.update("update parking_spots set enabled = false where id = ?", spotA1);
        createBooking(student, spotA1, start, start.plus(Duration.ofHours(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SPOT_NOT_BOOKABLE"));

        jdbc.update("update parking_lots set enabled = false where name like 'Lot A%'");
        createBooking(student, spotA2, start, start.plus(Duration.ofHours(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SPOT_NOT_BOOKABLE"));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        createBooking(student, spotA1, NOW.minus(Duration.ofMinutes(10)), NOW.plus(Duration.ofHours(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("START_IN_PAST"));
        createBooking(student, spotA1, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofMinutes(80)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING_TOO_SHORT"));
        postJson("/api/bookings", bookingBody(spotA1, "NOT_VALID!", NOW.plus(Duration.ofHours(1)),
                NOW.plus(Duration.ofHours(2))), student)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_LICENCE_PLATE"));
        postJson("/api/bookings", Map.of("plate", "ABC"), student)
                .andExpect(status().isBadRequest());
        createBooking(student, 999_999, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2)))
                .andExpect(status().isNotFound());
        createBooking("bad-token", spotA1, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2)))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from bookings", Long.class)).isZero();
    }

    @Test
    void quoteUsesCallersRateAndRoundsUp() throws Exception {
        mockMvc.perform(get("/api/pricing/quote")
                        .param("start", NOW.toString())
                        .param("end", NOW.plus(Duration.ofMinutes(90)).toString())
                        .with(bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hourlyRate").value(5.0))
                .andExpect(jsonPath("$.billableHours").value(2))
                .andExpect(jsonPath("$.total").value(10.0))
                .andExpect(jsonPath("$.deposit").value(5.0))
                .andExpect(jsonPath("$.finalCharge").value(5.0))
                .andExpect(jsonPath("$.currency").value("CAD"));

        mockMvc.perform(get("/api/pricing/quote").param("start", NOW.toString())
                        .param("end", NOW.plus(Duration.ofMinutes(10)).toString())
                        .with(bearer(student)))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/pricing/quote").param("start", NOW.toString())
                        .param("end", NOW.plus(Duration.ofHours(1)).toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void extendRecalculatesTotalAndChecksConflicts() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(2));
        Instant end = start.plus(Duration.ofHours(2));
        String id = createBookingId(student, spotA1, start, end);
        String other = registerAndLogin("other@example.com");
        createBookingId(other, spotA1, end.plus(Duration.ofHours(2)), end.plus(Duration.ofHours(3)));

        postJson("/api/bookings/" + id + "/extend", Map.of("newEnd", end.plus(Duration.ofMinutes(90)).toString()),
                student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endTime").value(end.plus(Duration.ofMinutes(90)).toString()))
                .andExpect(jsonPath("$.totalAmount").value(20.0)) // 3.5h -> 4 billable hours
                .andExpect(jsonPath("$.depositAmount").value(5.0));

        postJson("/api/bookings/" + id + "/extend", Map.of("newEnd", end.plus(Duration.ofHours(3)).toString()),
                student)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already booked")));
        postJson("/api/bookings/" + id + "/extend", Map.of("newEnd", end.toString()), student)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_NEW_END"));
        assertThat(payments(id)).containsExactly("DEPOSIT 5.00 SUCCEEDED");
    }

    @Test
    void cancellingAtLeastOneHourBeforeStartRefundsDeposit() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(3));
        String id = createBookingId(student, spotA1, start, start.plus(Duration.ofHours(2)));
        clock.setInstant(start.minus(Duration.ofHours(1)));

        postJson("/api/bookings/" + id + "/cancel", Map.of(), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").value(start.minus(Duration.ofHours(1)).toString()));

        assertThat(payments(id)).containsExactly("DEPOSIT 5.00 SUCCEEDED", "REFUND 5.00 SUCCEEDED");
        getWithToken("/api/bookings/" + id + "/receipt", student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCharged").value(5.0))
                .andExpect(jsonPath("$.totalRefunded").value(5.0))
                .andExpect(jsonPath("$.netPaid").value(0.0));
        postJson("/api/bookings/" + id + "/cancel", Map.of(), student)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_ACTIVE"));
    }

    @Test
    void lateCancellationKeepsDepositAndFreesTheSpot() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(2));
        String id = createBookingId(student, spotA1, start, start.plus(Duration.ofHours(2)));
        clock.setInstant(start.minus(Duration.ofMinutes(30)));

        postJson("/api/bookings/" + id + "/cancel", Map.of(), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(payments(id)).containsExactly("DEPOSIT 5.00 SUCCEEDED");
        createBooking(registerAndLogin("next@example.com"), spotA1, start, start.plus(Duration.ofHours(2)))
                .andExpect(status().isCreated());
    }

    @Test
    void checkoutAfterStartChargesTheRemainder() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(1));
        String id = createBookingId(student, spotA1, start, start.plus(Duration.ofHours(3)));

        postJson("/api/bookings/" + id + "/checkout", Map.of(), student)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CHECKOUT_TOO_EARLY"));

        Instant checkoutAt = start.plus(Duration.ofMinutes(10));
        clock.setInstant(checkoutAt);
        postJson("/api/bookings/" + id + "/checkout", Map.of(), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value(checkoutAt.toString()));

        assertThat(payments(id)).containsExactly("DEPOSIT 5.00 SUCCEEDED", "FINAL 10.00 SUCCEEDED");
        postJson("/api/bookings/" + id + "/checkout", Map.of(), student)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_ACTIVE"));
    }

    @Test
    void receiptListsBookingAndPayments() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(1));
        String id = createBookingId(student, spotA1, start, start.plus(Duration.ofHours(2)));
        clock.setInstant(start.plus(Duration.ofHours(1)));
        postJson("/api/bookings/" + id + "/checkout", Map.of(), student).andExpect(status().isOk());

        getWithToken("/api/bookings/" + id + "/receipt", student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.id").value(id))
                .andExpect(jsonPath("$.booking.status").value("COMPLETED"))
                .andExpect(jsonPath("$.customerEmail").value("student.demo@my.yorku.ca"))
                .andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.payments[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.payments[0].method").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.payments[1].type").value("FINAL"))
                .andExpect(jsonPath("$.payments[1].amount").value(5.0))
                .andExpect(jsonPath("$.totalCharged").value(10.0))
                .andExpect(jsonPath("$.totalRefunded").value(0.0))
                .andExpect(jsonPath("$.netPaid").value(10.0))
                .andExpect(jsonPath("$.currency").value("CAD"));
    }

    @Test
    void otherUsersCannotSeeOrActOnABookingButManagersCan() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(3));
        String id = createBookingId(student, spotA1, start, start.plus(Duration.ofHours(2)));
        String stranger = registerAndLogin("stranger@example.com");

        getWithToken("/api/bookings/" + id, stranger).andExpect(status().isNotFound());
        getWithToken("/api/bookings/" + id + "/receipt", stranger).andExpect(status().isNotFound());
        postJson("/api/bookings/" + id + "/cancel", Map.of(), stranger).andExpect(status().isNotFound());
        postJson("/api/bookings/" + id + "/checkout", Map.of(), stranger).andExpect(status().isNotFound());
        postJson("/api/bookings/" + id + "/extend",
                Map.of("newEnd", start.plus(Duration.ofHours(3)).toString()), stranger)
                .andExpect(status().isNotFound());
        getWithToken("/api/bookings/me", stranger)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        String manager = registerManager("manager@example.com");
        getWithToken("/api/bookings/" + id, manager).andExpect(status().isOk());
        getWithToken("/api/bookings/" + id + "/receipt", manager).andExpect(status().isOk());
        postJson("/api/bookings/" + id + "/extend",
                Map.of("newEnd", start.plus(Duration.ofHours(3)).toString()), manager)
                .andExpect(status().isForbidden());
        postJson("/api/bookings/" + id + "/cancel", Map.of(), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(payments(id)).containsExactly("DEPOSIT 5.00 SUCCEEDED", "REFUND 5.00 SUCCEEDED");
    }

    @Test
    void myBookingsArePaginatedAndFilterable() throws Exception {
        Instant start = NOW.plus(Duration.ofHours(2));
        String first = createBookingId(student, spotA1, start, start.plus(Duration.ofHours(1)));
        String second = createBookingId(student, spotA1, start.plus(Duration.ofHours(2)),
                start.plus(Duration.ofHours(3)));
        postJson("/api/bookings/" + first + "/cancel", Map.of(), student).andExpect(status().isOk());

        getWithToken("/api/bookings/me?size=1", student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second)); // latest start first
        getWithToken("/api/bookings/me?status=CANCELLED", student)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
        getWithToken("/api/bookings/me?size=500", student).andExpect(status().isBadRequest());
        getWithToken("/api/bookings/me?status=BOGUS", student).andExpect(status().isBadRequest());
    }

    @Test
    void unknownBookingIs404() throws Exception {
        mockMvc.perform(post("/api/bookings/00000000-0000-0000-0000-000000000000/cancel").with(bearer(student)))
                .andExpect(status().isNotFound());
    }

    private List<String> payments(String bookingId) {
        return jdbc.query("select type, amount, status from payments where booking_id = ?::uuid "
                        + "order by created_at, type",
                (rs, i) -> rs.getString("type") + " " + rs.getBigDecimal("amount").setScale(2) + " "
                        + rs.getString("status"),
                bookingId);
    }
}
