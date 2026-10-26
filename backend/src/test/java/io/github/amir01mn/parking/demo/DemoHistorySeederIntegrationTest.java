package io.github.amir01mn.parking.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.amir01mn.parking.analytics.StatsResponse;
import io.github.amir01mn.parking.analytics.StatsService;
import io.github.amir01mn.parking.booking.PricingService;
import io.github.amir01mn.parking.support.AbstractApiIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Runs the demo history seeder (a {@code demo}-profile bean, so it is built by hand here) against the real schema:
 * every constraint, including the exclusion constraints, must accept the data, and the dashboard figures must land
 * in the intended ranges.
 */
class DemoHistorySeederIntegrationTest extends AbstractApiIntegrationTest {

    private static final ZoneId CAMPUS = ZoneId.of("America/Toronto");

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private StatsService statsService;

    @AfterEach
    void removeSeededDirectoryEntries() {
        // the base class removes users and bookings; the 20 reference rows are the ones Flyway seeds
        jdbc.update("delete from university_directory where email <> all (?)", (Object) REFERENCE_DIRECTORY);
    }

    private static final String[] REFERENCE_DIRECTORY = {
        "aisha.rahman@my.yorku.ca", "daniel.chen@my.yorku.ca", "priya.patel@my.yorku.ca", "lucas.martin@my.yorku.ca",
        "sofia.rossi@my.yorku.ca", "omar.haddad@my.yorku.ca", "emily.nguyen@my.yorku.ca", "student.demo@my.yorku.ca",
        "margaret.wong@yorku.ca", "james.okafor@yorku.ca", "elena.petrova@yorku.ca", "robert.singh@yorku.ca",
        "hannah.kim@yorku.ca", "faculty.demo@yorku.ca", "carlos.mendes@yorku.ca", "fatima.ali@yorku.ca",
        "george.brown@yorku.ca", "linda.tremblay@yorku.ca", "kevin.osei@yorku.ca", "staff.demo@yorku.ca"};

    @Test
    void seedsAConsistentHistoryOnceAndFillsTheDashboard() {
        Instant now = ZonedDateTime.of(2026, 10, 26, 14, 20, 0, 0, CAMPUS).toInstant();
        clock.setInstant(now);
        DemoHistorySeeder seeder = new DemoHistorySeeder("Demo#2026", namedJdbc, passwordEncoder, pricingService,
                clock);

        int seeded = seeder.seed();
        assertThat(seeded).isGreaterThan(3000);
        assertThat(seeder.seed()).as("second run is a no-op").isZero();
        assertThat(jdbc.queryForObject("select count(*) from bookings", Integer.class)).isEqualTo(seeded);

        // directory entries give the drivers their tiers
        assertThat(jdbc.queryForObject("""
                select count(*) from users u join university_directory d on d.email = u.email
                where u.user_type <> d.user_type
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from users where user_type = 'VISITOR' and role = 'USER'", Integer.class))
                .isPositive();

        // payments agree with the booking amounts
        assertThat(jdbc.queryForObject("""
                select count(*) from bookings b
                where b.status = 'COMPLETED'
                  and (select coalesce(sum(p.amount), 0) from payments p where p.booking_id = b.id) <> b.total_amount
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from bookings b join payments p on p.booking_id = b.id and p.type = 'REFUND'
                where b.status <> 'CANCELLED' or b.cancelled_at > b.start_time - interval '1 hour'
                """, Integer.class)).isZero();

        LocalDate today = now.atZone(CAMPUS).toLocalDate();
        StatsResponse stats = statsService.stats(today.minusDays(29).atStartOfDay(CAMPUS).toInstant(),
                today.plusDays(1).atStartOfDay(CAMPUS).toInstant());
        assertThat(stats.lotUtilisation()).allSatisfy(lot ->
                assertThat(lot.utilisationPercent()).isBetween(new BigDecimal("10.0"), new BigDecimal("60.0")));
        assertThat(stats.revenueByUserType()).allSatisfy(r -> assertThat(r.revenue()).isPositive());
        assertThat(stats.bookingsPerDay()).hasSize(30);
        assertThat(jdbc.queryForObject("""
                select count(*) from bookings where status = 'CONFIRMED' and start_time <= ? and end_time > ?
                """, Integer.class, Timestamp.from(now), Timestamp.from(now))).isPositive();
    }
}
