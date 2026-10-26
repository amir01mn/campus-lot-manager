package io.github.amir01mn.parking.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.amir01mn.parking.booking.BookingStatus;
import io.github.amir01mn.parking.booking.PricingService;
import io.github.amir01mn.parking.config.PricingProperties;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.Driver;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.Interval;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.Plan;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.PlannedBooking;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.PlannedPayment;
import io.github.amir01mn.parking.demo.DemoHistoryPlanner.SpotInfo;
import io.github.amir01mn.parking.payment.PaymentType;
import io.github.amir01mn.parking.user.UserType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DemoHistoryPlannerTest {

    private static final ZoneId CAMPUS = ZoneId.of("America/Toronto");
    /** A Monday afternoon in term. */
    private static final Instant NOW = ZonedDateTime.of(2026, 10, 26, 14, 20, 0, 0, CAMPUS).toInstant();

    private final PricingService pricing = new PricingService(new PricingProperties("CAD",
            new PricingProperties.Rates(new BigDecimal("5.00"), new BigDecimal("8.00"), new BigDecimal("10.00"),
                    new BigDecimal("15.00"))));
    private final DemoHistoryPlanner planner = new DemoHistoryPlanner(pricing, CAMPUS);
    private final List<SpotInfo> spots = campusSpots();

    /** Lots A, B and C with 40, 60 and 30 spots, like the seeded reference data; A-040 is out of service. */
    private static List<SpotInfo> campusSpots() {
        List<SpotInfo> spots = new ArrayList<>();
        long id = 1;
        for (Object[] lot : new Object[][] {{"A", 40}, {"B", 60}, {"C", 30}}) {
            for (int n = 1; n <= (Integer) lot[1]; n++) {
                spots.add(new SpotInfo(id++, (String) lot[0], !("A".equals(lot[0]) && n == 40)));
            }
        }
        return spots;
    }

    private List<Driver> drivers() {
        return planner.drivers(LocalDate.of(2026, 10, 26), Set.of("aisha.rahman@my.yorku.ca"));
    }

    private Plan plan() {
        return planner.plan(drivers(), spots, NOW, Map.of(), Set.of());
    }

    @Test
    void isDeterministic() {
        Plan first = plan();
        Plan second = plan();
        assertThat(second.bookings()).isEqualTo(first.bookings());
        assertThat(second.payments()).isEqualTo(first.payments());
        assertThat(drivers()).isEqualTo(drivers());
    }

    @Test
    void driversCoverEveryTierWithUniqueAddressesMatchingTheirTier() {
        List<Driver> drivers = drivers();
        assertThat(drivers).extracting(Driver::email).doesNotHaveDuplicates()
                .doesNotContain("aisha.rahman@my.yorku.ca");
        Map<UserType, Long> byType = drivers.stream().collect(Collectors.groupingBy(Driver::type,
                () -> new EnumMap<>(UserType.class), Collectors.counting()));
        assertThat(byType).containsOnlyKeys(UserType.values());
        for (Driver driver : drivers) {
            String domain = switch (driver.type()) {
                case STUDENT -> "@my.yorku.ca";
                case FACULTY, STAFF -> "@yorku.ca";
                case VISITOR -> "@example.com";
            };
            assertThat(driver.email()).endsWith(domain).isLowerCase();
            assertThat(driver.plate()).matches("^[A-Z0-9 -]{2,10}$");
        }
    }

    @Test
    void bookingsFollowTheBookingRulesAndPricing() {
        Plan plan = plan();
        assertThat(plan.bookings()).hasSizeGreaterThan(3000);
        for (PlannedBooking b : plan.bookings()) {
            Duration duration = Duration.between(b.start(), b.end());
            assertThat(duration).isBetween(Duration.ofMinutes(30), Duration.ofHours(24));
            assertThat(b.hourlyRate()).isEqualByComparingTo(pricing.hourlyRate(b.driver().type()));
            assertThat(b.depositAmount()).isEqualByComparingTo(pricing.deposit(b.hourlyRate()));
            assertThat(b.totalAmount()).isEqualByComparingTo(pricing.total(b.hourlyRate(), b.start(), b.end()));
            assertThat(b.referenceCode()).matches("^CP-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$");
            assertThat(b.createdAt()).isBeforeOrEqualTo(NOW).isBeforeOrEqualTo(b.start())
                    .isAfterOrEqualTo(b.driver().createdAt());
            switch (b.status()) {
                case COMPLETED -> {
                    assertThat(b.end()).isBefore(NOW);
                    assertThat(b.completedAt()).isAfter(b.start()).isBeforeOrEqualTo(NOW);
                    assertThat(b.cancelledAt()).isNull();
                }
                case CANCELLED -> {
                    assertThat(b.cancelledAt()).isAfter(b.createdAt()).isBefore(b.end()).isBeforeOrEqualTo(NOW);
                    assertThat(b.completedAt()).isNull();
                }
                case CONFIRMED -> {
                    assertThat(b.end()).isAfter(NOW.minus(Duration.ofMinutes(2)));
                    assertThat(b.cancelledAt()).isNull();
                    assertThat(b.completedAt()).isNull();
                    assertThat(spots.get((int) b.spotId() - 1).bookable()).isTrue();
                }
            }
        }
        assertThat(plan.bookings()).extracting(PlannedBooking::referenceCode).doesNotHaveDuplicates();
        assertThat(plan.bookings()).extracting(PlannedBooking::id).doesNotHaveDuplicates();
    }

    @Test
    void paymentsMatchStatusesAndTheRefundRule() {
        Plan plan = plan();
        Map<UUID, List<PlannedPayment>> byBooking = plan.payments().stream()
                .collect(Collectors.groupingBy(PlannedPayment::bookingId));
        int refunds = 0;
        int keptDeposits = 0;
        for (PlannedBooking b : plan.bookings()) {
            Map<PaymentType, PlannedPayment> payments = byBooking.get(b.id()).stream()
                    .collect(Collectors.toMap(PlannedPayment::type, Function.identity()));
            assertThat(payments.get(PaymentType.DEPOSIT).amount()).isEqualByComparingTo(b.depositAmount());
            assertThat(payments.get(PaymentType.DEPOSIT).createdAt()).isEqualTo(b.createdAt());
            BigDecimal finalCharge = pricing.finalCharge(b.totalAmount(), b.depositAmount());
            if (b.status() == BookingStatus.COMPLETED && finalCharge.signum() > 0) {
                assertThat(payments.get(PaymentType.FINAL).amount()).isEqualByComparingTo(finalCharge);
                assertThat(payments.get(PaymentType.FINAL).createdAt()).isEqualTo(b.completedAt());
            } else {
                assertThat(payments).doesNotContainKey(PaymentType.FINAL);
            }
            boolean refundDue = b.status() == BookingStatus.CANCELLED
                    && !b.cancelledAt().isAfter(b.start().minus(Duration.ofHours(1)));
            if (refundDue) {
                refunds++;
                assertThat(payments.get(PaymentType.REFUND).amount()).isEqualByComparingTo(b.depositAmount());
                assertThat(payments.get(PaymentType.REFUND).createdAt()).isEqualTo(b.cancelledAt());
            } else {
                if (b.status() == BookingStatus.CANCELLED) {
                    keptDeposits++;
                }
                assertThat(payments).doesNotContainKey(PaymentType.REFUND);
            }
        }
        assertThat(refunds).isPositive();
        assertThat(keptDeposits).isPositive();
    }

    @Test
    void nonCancelledBookingsNeverOverlapOnASpotOrForADriverOrWithExistingBookings() {
        Instant tomorrowMorning = ZonedDateTime.of(2026, 10, 27, 8, 0, 0, 0, CAMPUS).toInstant();
        Interval existing = new Interval(tomorrowMorning, tomorrowMorning.plus(Duration.ofHours(10)));
        Map<Long, List<Interval>> busy = new HashMap<>();
        for (long spot = 1; spot <= 130; spot += 2) {
            busy.put(spot, List.of(existing));
        }
        Plan plan = planner.plan(drivers(), spots, NOW, busy, Set.of());

        Map<Long, List<Interval>> bySpot = new HashMap<>(Map.of());
        Map<UUID, List<Interval>> byDriver = new HashMap<>();
        busy.forEach((spot, intervals) -> bySpot.put(spot, new ArrayList<>(intervals)));
        for (PlannedBooking b : plan.bookings()) {
            if (b.status() != BookingStatus.CANCELLED) {
                bySpot.computeIfAbsent(b.spotId(), k -> new ArrayList<>()).add(new Interval(b.start(), b.end()));
                byDriver.computeIfAbsent(b.driver().id(), k -> new ArrayList<>()).add(new Interval(b.start(), b.end()));
            }
        }
        bySpot.values().forEach(DemoHistoryPlannerTest::assertNoOverlap);
        byDriver.values().forEach(DemoHistoryPlannerTest::assertNoOverlap);
    }

    @Test
    void producesABusyButPlausibleDashboard() {
        Plan plan = plan();
        // the dashboard's default range: the last 30 campus days, today included
        LocalDate today = LocalDate.of(2026, 10, 26);
        Instant from = today.minusDays(29).atStartOfDay(CAMPUS).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(CAMPUS).toInstant();

        Map<String, Double> bookedHours = new TreeMap<>();
        Map<String, Integer> spotsPerLot = new TreeMap<>();
        spots.forEach(s -> spotsPerLot.merge(s.lot(), 1, Integer::sum));
        Map<LocalDate, Integer> perDay = new TreeMap<>();
        Map<UserType, BigDecimal> revenue = new EnumMap<>(UserType.class);
        Map<Long, String> lotOfSpot = spots.stream().collect(Collectors.toMap(SpotInfo::id, SpotInfo::lot));
        Map<UUID, PlannedBooking> bookingsById = plan.bookings().stream()
                .collect(Collectors.toMap(PlannedBooking::id, Function.identity()));
        for (PlannedBooking b : plan.bookings()) {
            if (!b.start().isBefore(from) && b.start().isBefore(to)) {
                perDay.merge(b.start().atZone(CAMPUS).toLocalDate(), 1, Integer::sum);
            }
            if (b.status() != BookingStatus.CANCELLED && b.start().isBefore(to) && b.end().isAfter(from)) {
                Instant s = b.start().isBefore(from) ? from : b.start();
                Instant e = b.end().isAfter(to) ? to : b.end();
                bookedHours.merge(lotOfSpot.get(b.spotId()), Duration.between(s, e).toMinutes() / 60.0, Double::sum);
            }
        }
        for (PlannedPayment p : plan.payments()) {
            if (!p.createdAt().isBefore(from) && p.createdAt().isBefore(to)) {
                BigDecimal signed = p.type() == PaymentType.REFUND ? p.amount().negate() : p.amount();
                revenue.merge(bookingsById.get(p.bookingId()).driver().type(), signed, BigDecimal::add);
            }
        }
        double rangeHours = Duration.between(from, to).toMinutes() / 60.0;
        double total = bookedHours.values().stream().mapToDouble(Double::doubleValue).sum();
        double overall = total / (spots.size() * rangeHours);
        assertThat(overall).as("overall utilisation").isBetween(0.20, 0.60);
        bookedHours.forEach((lot, hours) -> assertThat(hours / (spotsPerLot.get(lot) * rangeHours))
                .as("utilisation of lot " + lot).isBetween(0.10, 0.70));

        assertThat(perDay).hasSize(30);
        int busiest = perDay.values().stream().max(Comparator.naturalOrder()).orElseThrow();
        int quietest = perDay.values().stream().min(Comparator.naturalOrder()).orElseThrow();
        assertThat(quietest).as("weekends and holidays are quieter").isLessThan(busiest / 2);

        assertThat(revenue).containsOnlyKeys(UserType.values());
        revenue.values().forEach(amount -> assertThat(amount).isPositive());

        long activeNow = plan.bookings().stream().filter(b -> b.status() == BookingStatus.CONFIRMED
                && !b.start().isAfter(NOW) && b.end().isAfter(NOW)).count();
        long upcoming = plan.bookings().stream().filter(b -> b.status() == BookingStatus.CONFIRMED
                && b.start().isAfter(NOW)).count();
        assertThat(activeNow).as("cars parked right now").isBetween(30L, 110L);
        assertThat(upcoming).as("bookings for the coming days").isGreaterThan(80);
    }

    @Test
    void recognisesCampusHolidays() {
        assertThat(DemoHistoryPlanner.isHoliday(LocalDate.of(2026, 10, 12))).isTrue();  // Thanksgiving
        assertThat(DemoHistoryPlanner.isHoliday(LocalDate.of(2026, 9, 7))).isTrue();    // Labour Day
        assertThat(DemoHistoryPlanner.isHoliday(LocalDate.of(2026, 12, 28))).isTrue();  // winter closure
        assertThat(DemoHistoryPlanner.isHoliday(LocalDate.of(2026, 10, 19))).isFalse();
    }

    private static void assertNoOverlap(List<Interval> intervals) {
        List<Interval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(Interval::start));
        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).start()).as("overlap").isAfterOrEqualTo(sorted.get(i - 1).end());
        }
    }
}
