package io.github.amir01mn.parking.analytics;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Manager booking search and dashboard stats on a controlled dataset (rows inserted directly, fixed clock).
 * Range: 1-3 March 2030 in Toronto (EST, UTC-5), i.e. [2030-03-01T05:00Z, 2030-03-04T05:00Z) = 72 hours.
 */
class ManagerReportsIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-03-10T15:00:00Z");
    private static final String FROM = "2030-03-01T05:00:00Z";
    private static final String TO = "2030-03-04T05:00:00Z";

    private String manager;
    private UUID b1;
    private UUID b2;
    private UUID b3;
    private UUID b4;
    private UUID b5;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        manager = registerManager("manager@yorku.ca");
        register("student.demo@my.yorku.ca", PASSWORD); // STUDENT
        register("faculty.demo@yorku.ca", PASSWORD); // FACULTY
        register("visitor@example.com", PASSWORD); // VISITOR

        // Lot A, Mar 1 09:00-11:00 local, completed: deposit paid before the range, final inside it
        b1 = booking("CP-AAA001", "student.demo@my.yorku.ca", "SNS-A-001", "ABC 111",
                "2030-03-01T14:00:00Z", "2030-03-01T16:00:00Z", "COMPLETED", "5.00", "10.00", "5.00");
        payment(b1, "DEPOSIT", "5.00", "SUCCEEDED", "2030-02-28T12:00:00Z");
        payment(b1, "FINAL", "5.00", "SUCCEEDED", "2030-03-01T16:00:00Z");
        // Lot A, Mar 2 10:00-13:00 local, completed
        b2 = booking("CP-BBB002", "faculty.demo@yorku.ca", "SNS-A-002", "XYZ 222",
                "2030-03-02T15:00:00Z", "2030-03-02T18:00:00Z", "COMPLETED", "8.00", "24.00", "8.00");
        payment(b2, "DEPOSIT", "8.00", "SUCCEEDED", "2030-03-02T10:00:00Z");
        payment(b2, "FINAL", "16.00", "SUCCEEDED", "2030-03-02T18:00:00Z");
        // Lot B, Mar 2 15:00-16:00 local, cancelled and refunded (excluded from utilisation)
        b3 = booking("CP-CCC003", "visitor@example.com", "SNS-B-001", "VIS 333",
                "2030-03-02T20:00:00Z", "2030-03-02T21:00:00Z", "CANCELLED", "15.00", "15.00", "15.00");
        payment(b3, "DEPOSIT", "15.00", "SUCCEEDED", "2030-03-02T12:00:00Z");
        payment(b3, "REFUND", "15.00", "SUCCEEDED", "2030-03-02T13:00:00Z");
        // Lot B, Mar 3 22:00 - Mar 4 01:00 local: 2 of its 3 hours fall inside the range; a failed charge
        b4 = booking("CP-DDD004", "student.demo@my.yorku.ca", "SNS-B-002", "ABC 111",
                "2030-03-04T03:00:00Z", "2030-03-04T06:00:00Z", "CONFIRMED", "5.00", "15.00", "5.00");
        payment(b4, "DEPOSIT", "5.00", "SUCCEEDED", "2030-03-03T12:00:00Z");
        payment(b4, "FINAL", "10.00", "FAILED", "2030-03-03T13:00:00Z");
        // outside the range entirely
        b5 = booking("CP-EEE005", "student.demo@my.yorku.ca", "SNS-C-001", "ABC 111",
                "2030-03-05T15:00:00Z", "2030-03-05T16:00:00Z", "CONFIRMED", "5.00", "5.00", "5.00");
        payment(b5, "DEPOSIT", "5.00", "SUCCEEDED", "2030-03-05T12:00:00Z");

        jdbc.update("update parking_spots set occupied = true where id in "
                + "(select id from parking_spots order by id limit 13)");
    }

    @Test
    void statsOnAControlledDataset() throws Exception {
        getWithToken("/api/manager/stats?from=" + FROM + "&to=" + TO, manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(FROM))
                .andExpect(jsonPath("$.to").value(TO))
                .andExpect(jsonPath("$.timeZone").value("America/Toronto"))
                .andExpect(jsonPath("$.currency").value("CAD"))
                .andExpect(jsonPath("$.totalSpots").value(130))
                .andExpect(jsonPath("$.occupiedSpots").value(13))
                .andExpect(jsonPath("$.occupancyPercent").value(10.0))
                .andExpect(jsonPath("$.bookingsCount").value(4))
                .andExpect(jsonPath("$.bookingsByStatus.COMPLETED").value(2))
                .andExpect(jsonPath("$.bookingsByStatus.CANCELLED").value(1))
                .andExpect(jsonPath("$.bookingsByStatus.CONFIRMED").value(1))
                // 5 + (8 + 16) + (15 - 15) + 5; b1's deposit and b4's failed charge are excluded
                .andExpect(jsonPath("$.revenue").value(34.00))
                .andExpect(jsonPath("$.bookingsPerDay.length()").value(3))
                .andExpect(jsonPath("$.bookingsPerDay[0].date").value("2030-03-01"))
                .andExpect(jsonPath("$.bookingsPerDay[0].count").value(1))
                .andExpect(jsonPath("$.bookingsPerDay[1].date").value("2030-03-02"))
                .andExpect(jsonPath("$.bookingsPerDay[1].count").value(2))
                .andExpect(jsonPath("$.bookingsPerDay[2].date").value("2030-03-03"))
                .andExpect(jsonPath("$.bookingsPerDay[2].count").value(1))
                .andExpect(jsonPath("$.revenueByUserType.length()").value(4))
                .andExpect(jsonPath("$.revenueByUserType[?(@.userType == 'STUDENT')].revenue").value(10.00))
                .andExpect(jsonPath("$.revenueByUserType[?(@.userType == 'FACULTY')].revenue").value(24.00))
                .andExpect(jsonPath("$.revenueByUserType[?(@.userType == 'STAFF')].revenue").value(0.00))
                .andExpect(jsonPath("$.revenueByUserType[?(@.userType == 'VISITOR')].revenue").value(0.00))
                .andExpect(jsonPath("$.lotUtilisation.length()").value(3))
                .andExpect(jsonPath("$.lotUtilisation[0].lotName").value("Lot A — Keele Campus North"))
                .andExpect(jsonPath("$.lotUtilisation[0].totalSpots").value(40))
                .andExpect(jsonPath("$.lotUtilisation[0].bookedHours").value(5.00))
                .andExpect(jsonPath("$.lotUtilisation[0].availableSpotHours").value(2880.00))
                .andExpect(jsonPath("$.lotUtilisation[0].utilisationPercent").value(0.2))
                .andExpect(jsonPath("$.lotUtilisation[1].bookedHours").value(2.00))
                .andExpect(jsonPath("$.lotUtilisation[1].availableSpotHours").value(4320.00))
                .andExpect(jsonPath("$.lotUtilisation[1].utilisationPercent").value(0.0))
                .andExpect(jsonPath("$.lotUtilisation[2].bookedHours").value(0.00))
                .andExpect(jsonPath("$.lotUtilisation[2].availableSpotHours").value(2160.00));
    }

    @Test
    void statsDefaultToTheLastThirtyDaysAndValidateTheRange() throws Exception {
        getWithToken("/api/manager/stats", manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value(NOW.toString()))
                .andExpect(jsonPath("$.from").value("2030-02-08T15:00:00Z"))
                .andExpect(jsonPath("$.bookingsPerDay.length()").value(31))
                .andExpect(jsonPath("$.bookingsCount").value(5))
                .andExpect(jsonPath("$.revenue").value(44.00));

        getWithToken("/api/manager/stats?from=" + TO + "&to=" + FROM, manager)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
        getWithToken("/api/manager/stats?from=2028-01-01T00:00:00Z&to=" + TO, manager)
                .andExpect(jsonPath("$.code").value("RANGE_TOO_LONG"));
        getWithToken("/api/manager/stats?from=yesterday", manager).andExpect(status().isBadRequest());
    }

    @Test
    void bookingSearchFilters() throws Exception {
        getWithToken("/api/manager/bookings", manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].booking.id").value(b5.toString()))
                .andExpect(jsonPath("$.content[0].booking.spot.label").value("C-001"))
                .andExpect(jsonPath("$.content[0].customer.email").value("student.demo@my.yorku.ca"))
                .andExpect(jsonPath("$.content[0].customer.userType").value("STUDENT"));

        getWithToken("/api/manager/bookings?status=COMPLETED", manager)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].booking.id").value(b2.toString()))
                .andExpect(jsonPath("$.content[1].booking.id").value(b1.toString()));

        getWithToken("/api/manager/bookings?lotId=" + lotId("Lot B"), manager)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].booking.id").value(b4.toString()))
                .andExpect(jsonPath("$.content[1].booking.id").value(b3.toString()));

        // overlap with [Mar 2 00:00Z, Mar 3 00:00Z)
        getWithToken("/api/manager/bookings?from=2030-03-02T00:00:00Z&to=2030-03-03T00:00:00Z", manager)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].booking.id").value(b3.toString()))
                .andExpect(jsonPath("$.content[1].booking.id").value(b2.toString()));

        getWithToken("/api/manager/bookings?q=ccc0", manager)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].booking.referenceCode").value("CP-CCC003"));
        getWithToken("/api/manager/bookings?q=abc 1&status=CONFIRMED&size=1", manager)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].booking.id").value(b5.toString()));
        getWithToken("/api/manager/bookings?q=%25", manager).andExpect(jsonPath("$.totalElements").value(0));

        getWithToken("/api/manager/bookings?from=" + TO + "&to=" + FROM, manager)
                .andExpect(status().isUnprocessableEntity());
        getWithToken("/api/manager/bookings?status=NOPE", manager).andExpect(status().isBadRequest());
    }

    private UUID booking(String reference, String email, String sensorId, String plate, String start, String end,
                         String status, String rate, String total, String deposit) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        insert into bookings (id, reference_code, user_id, spot_id, licence_plate, start_time, end_time,
                            status, payment_method, hourly_rate, deposit_amount, total_amount, created_at)
                        select ?, ?, u.id, ?, ?, ?, ?, ?, 'CREDIT_CARD', ?, ?, ?, ? from users u where u.email = ?
                        """, id, reference, spotId(sensorId), plate, ts(start), ts(end), status,
                new BigDecimal(rate), new BigDecimal(deposit), new BigDecimal(total),
                ts("2030-02-20T12:00:00Z"), email);
        return id;
    }

    private void payment(UUID bookingId, String type, String amount, String status, String at) {
        jdbc.update("insert into payments (id, booking_id, type, amount, method, status, created_at) "
                        + "values (?, ?, ?, ?, 'CREDIT_CARD', ?, ?)",
                UUID.randomUUID(), bookingId, type, new BigDecimal(amount), status, ts(at));
    }

    private static Timestamp ts(String instant) {
        return Timestamp.from(Instant.parse(instant));
    }
}
