package io.github.amir01mn.parking.demo;

import io.github.amir01mn.parking.booking.BookingStatus;
import io.github.amir01mn.parking.booking.PricingService;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.Driver;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.Interval;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.Persona;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.Plan;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.PlannedBooking;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.PlannedPayment;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.SpotInfo;
import io.github.amir01mn.parking.user.PasswordPolicy;
import io.github.amir01mn.parking.user.Role;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code demo} profile only, after {@link DemoDataSeeder}: fills a fresh database with the background activity a
 * real campus would have, so the manager dashboard shows meaningful trends. It adds the drivers planned by
 * {@link DemoHistoryPlanner} (with university directory entries for their tiers), about two months of completed and
 * cancelled bookings with their payments, and confirmed bookings for now and the coming week.
 *
 * <p>Idempotent: nothing happens when any of the planned drivers already exists. Everything is written in one
 * transaction with JDBC batch inserts (one password hash for all drivers), so startup stays fast. Bookings are
 * inserted in spot-id order, the same lock order the sensor ingestion uses, so the two can't deadlock.
 */
@Component
@Profile("demo")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class DemoHistorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoHistorySeeder.class);
    private static final int BATCH_SIZE = 500;

    private final String demoPassword;
    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final PricingService pricingService;
    private final Clock clock;

    public DemoHistorySeeder(@Value("${app.demo.password:}") String demoPassword, NamedParameterJdbcTemplate jdbc,
                             PasswordEncoder passwordEncoder, PricingService pricingService, Clock clock) {
        this.demoPassword = demoPassword;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.pricingService = pricingService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Seeds once; returns the number of bookings written (0 when skipped). */
    @Transactional
    public int seed() {
        if (!PasswordPolicy.isAcceptable(demoPassword)) {
            log.warn("Demo history skipped: app.demo.password is missing or does not meet the password policy");
            return 0;
        }
        long started = System.nanoTime();
        Instant now = clock.instant();
        DemoHistoryPlanner planner = new DemoHistoryPlanner(pricingService, clock.getZone());
        // independent of the database contents, so every run plans the same drivers (that is the idempotency key)
        Set<String> reserved = new HashSet<>();
        DemoDataSeeder.ACCOUNTS.forEach(account -> reserved.add(account.email()));
        List<Driver> drivers = planner.drivers(LocalDate.now(clock), reserved);

        List<String> emails = drivers.stream().map(Driver::email).toList();
        Integer existing = jdbc.queryForObject("select count(*) from users where email in (:emails)",
                new MapSqlParameterSource("emails", emails), Integer.class);
        if (existing != null && existing > 0) {
            log.info("Demo history already present; skipping");
            return 0;
        }

        List<SpotInfo> spots = jdbc.getJdbcTemplate().query("""
                select s.id, s.label, (s.enabled and not s.maintenance_required and l.enabled) as bookable
                from parking_spots s join parking_lots l on l.id = s.lot_id
                order by s.id
                """, (rs, i) -> new SpotInfo(rs.getLong("id"), lotPrefix(rs.getString("label")),
                rs.getBoolean("bookable")));
        Map<Long, List<Interval>> busy = new HashMap<>();
        jdbc.query("""
                select spot_id, start_time, end_time from bookings
                where status <> 'CANCELLED' and end_time > :from
                """, new MapSqlParameterSource("from",
                        utc(now.minus(Duration.ofDays(DemoHistoryPlanner.HISTORY_DAYS + 2)))),
                rs -> {
                    busy.computeIfAbsent(rs.getLong("spot_id"), k -> new ArrayList<>()).add(new Interval(
                            rs.getObject("start_time", OffsetDateTime.class).toInstant(),
                            rs.getObject("end_time", OffsetDateTime.class).toInstant()));
                });
        Set<String> references = new HashSet<>(jdbc.getJdbcTemplate()
                .queryForList("select reference_code from bookings", String.class));

        Plan plan = planner.plan(drivers, spots, now, busy, references);
        insertDrivers(drivers);
        insertBookings(DemoHistoryPlanner.insertOrder(plan.bookings()));
        insertPayments(plan.payments());

        Map<BookingStatus, Integer> byStatus = new HashMap<>();
        plan.bookings().forEach(b -> byStatus.merge(b.status(), 1, Integer::sum));
        log.info("Seeded demo history: {} drivers, {} bookings {} and {} payments in {} ms", drivers.size(),
                plan.bookings().size(), byStatus, plan.payments().size(), (System.nanoTime() - started) / 1_000_000);
        return plan.bookings().size();
    }

    private void insertDrivers(List<Driver> drivers) {
        List<MapSqlParameterSource> directory = new ArrayList<>();
        List<MapSqlParameterSource> users = new ArrayList<>();
        String passwordHash = passwordEncoder.encode(demoPassword);
        for (Driver driver : drivers) {
            if (driver.persona() != Persona.VISITOR) {
                directory.add(new MapSqlParameterSource()
                        .addValue("email", driver.email())
                        .addValue("type", driver.type().name()));
            }
            users.add(new MapSqlParameterSource()
                    .addValue("id", driver.id())
                    .addValue("email", driver.email())
                    .addValue("hash", passwordHash)
                    .addValue("first", driver.firstName())
                    .addValue("last", driver.lastName())
                    .addValue("type", driver.type().name())
                    .addValue("role", Role.USER.name())
                    .addValue("created", utc(driver.createdAt())));
        }
        batch("""
                insert into university_directory (email, user_type) values (:email, :type)
                on conflict (email) do nothing
                """, directory);
        batch("""
                insert into users (id, email, password_hash, first_name, last_name, user_type, role, created_at)
                values (:id, :email, :hash, :first, :last, :type, :role, :created)
                """, users);
    }

    private void insertBookings(List<PlannedBooking> bookings) {
        List<MapSqlParameterSource> rows = new ArrayList<>(bookings.size());
        for (PlannedBooking b : bookings) {
            rows.add(new MapSqlParameterSource()
                    .addValue("id", b.id())
                    .addValue("ref", b.referenceCode())
                    .addValue("user", b.driver().id())
                    .addValue("spot", b.spotId())
                    .addValue("plate", b.driver().plate())
                    .addValue("start", utc(b.start()))
                    .addValue("end", utc(b.end()))
                    .addValue("status", b.status().name())
                    .addValue("method", b.driver().paymentMethod().name())
                    .addValue("rate", b.hourlyRate())
                    .addValue("deposit", b.depositAmount())
                    .addValue("total", b.totalAmount())
                    .addValue("created", utc(b.createdAt()))
                    .addValue("cancelled", utc(b.cancelledAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                    .addValue("completed", utc(b.completedAt()), Types.TIMESTAMP_WITH_TIMEZONE));
        }
        batch("""
                insert into bookings (id, reference_code, user_id, spot_id, licence_plate, start_time, end_time, status,
                                      payment_method, hourly_rate, deposit_amount, total_amount, created_at,
                                      cancelled_at, completed_at)
                values (:id, :ref, :user, :spot, :plate, :start, :end, :status, :method, :rate, :deposit, :total,
                        :created, :cancelled, :completed)
                """, rows);
    }

    private void insertPayments(List<PlannedPayment> payments) {
        List<MapSqlParameterSource> rows = new ArrayList<>(payments.size());
        for (PlannedPayment p : payments) {
            rows.add(new MapSqlParameterSource()
                    .addValue("id", p.id())
                    .addValue("booking", p.bookingId())
                    .addValue("type", p.type().name())
                    .addValue("amount", p.amount())
                    .addValue("method", p.method().name())
                    .addValue("ref", p.providerReference())
                    .addValue("created", utc(p.createdAt())));
        }
        batch("""
                insert into payments (id, booking_id, type, amount, method, status, provider_reference, created_at)
                values (:id, :booking, :type, :amount, :method, 'SUCCEEDED', :ref, :created)
                """, rows);
    }

    private void batch(String sql, List<MapSqlParameterSource> rows) {
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<MapSqlParameterSource> chunk = rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE));
            jdbc.batchUpdate(sql, chunk.toArray(MapSqlParameterSource[]::new));
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** {@code A-001} → {@code A}; {@code L12-104} → {@code L12}. */
    static String lotPrefix(String label) {
        int dash = label.indexOf('-');
        return dash > 0 ? label.substring(0, dash) : label;
    }
}
