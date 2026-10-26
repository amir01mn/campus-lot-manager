package io.github.amir01mn.parking.demo;

import io.github.amir01mn.parking.booking.BookingStatus;
import io.github.amir01mn.parking.booking.PricingService;
import io.github.amir01mn.parking.booking.ReferenceCodeGenerator;
import io.github.amir01mn.parking.payment.PaymentMethod;
import io.github.amir01mn.parking.payment.PaymentType;
import io.github.amir01mn.parking.user.UserType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pure, deterministic generator of the demo profile's background activity: a population of drivers with habits
 * (residence students, commuters, faculty, staff, visitors) and about two months of their bookings and payments,
 * plus confirmed bookings for now and the coming days.
 *
 * <p>Everything the real application enforces holds for the generated data: no two non-cancelled bookings overlap
 * on a spot or for a driver, durations are 30 minutes to 24 hours, amounts come from {@link PricingService}
 * (deposit = one hour, total = rate x billable hours rounded up, FINAL = total - deposit), a cancellation is refunded
 * exactly when it happened at least one hour before the start, and nothing is created, cancelled or completed after
 * {@code now}. Confirmed bookings use only currently bookable spots. Output depends only on the seeds, {@code now},
 * the zone and the inputs, so a fresh database always gets the same picture relative to today.
 */
final class DemoHistoryPlanner {

    /** Seed of the driver population (names, habits, home lots). */
    static final long DRIVER_SEED = 3311L;
    /** Seed of the booking history. */
    static final long HISTORY_SEED = 3311_2026L;

    static final int HISTORY_DAYS = 60;
    static final int FUTURE_DAYS = 7;

    /** Habits drive when, how long and how often a driver parks. */
    enum Persona {
        RESIDENT(UserType.STUDENT, "my.yorku.ca"),
        COMMUTER(UserType.STUDENT, "my.yorku.ca"),
        FACULTY(UserType.FACULTY, "yorku.ca"),
        STAFF(UserType.STAFF, "yorku.ca"),
        VISITOR(UserType.VISITOR, "example.com");

        final UserType type;
        final String domain;

        Persona(UserType type, String domain) {
            this.type = type;
            this.domain = domain;
        }
    }

    /** How many drivers of each persona exist (see the Phase 5 decision log entry for the sizing). */
    static final Map<Persona, Integer> POPULATION = populationOf(
            Persona.RESIDENT, 28, Persona.COMMUTER, 28, Persona.FACULTY, 26, Persona.STAFF, 34, Persona.VISITOR, 26);

    record Driver(UUID id, String email, String firstName, String lastName, Persona persona, String plate,
                  PaymentMethod paymentMethod, String homeLot, Instant createdAt) {

        UserType type() {
            return persona.type;
        }
    }

    /** A parking spot as the planner sees it; {@code lot} is the label prefix ({@code A}, {@code B}, {@code L12}). */
    record SpotInfo(long id, String lot, boolean bookable) {
    }

    record Interval(Instant start, Instant end) {
    }

    record PlannedBooking(UUID id, String referenceCode, Driver driver, long spotId, Instant start, Instant end,
                          BookingStatus status, BigDecimal hourlyRate, BigDecimal depositAmount,
                          BigDecimal totalAmount, Instant createdAt, Instant cancelledAt, Instant completedAt) {
    }

    record PlannedPayment(UUID id, UUID bookingId, PaymentType type, BigDecimal amount, PaymentMethod method,
                          String providerReference, Instant createdAt) {
    }

    record Plan(List<PlannedBooking> bookings, List<PlannedPayment> payments) {
    }

    private static final String[] FIRST_NAMES = {
        "Aarav", "Abigail", "Adebayo", "Aiden", "Alejandro", "Amara", "Ananya", "Arjun", "Beatriz", "Benjamin",
        "Caleb", "Camila", "Chen", "Chiara", "Daniela", "Darius", "Dev", "Elif", "Elijah", "Emeka",
        "Farah", "Gabriel", "Grace", "Hamza", "Harper", "Ines", "Isaac", "Jasmine", "Javier", "Jin",
        "Kai", "Kavya", "Layla", "Leo", "Lucia", "Malik", "Maya", "Mateo", "Mei", "Mohammed",
        "Nadia", "Nathan", "Nia", "Nikhil", "Oliver", "Parisa", "Quinn", "Rania", "Rohan", "Rosa",
        "Samuel", "Sana", "Theo", "Tomas", "Valentina", "Wei", "Xavier", "Yara", "Yusuf", "Zara"};
    private static final String[] LAST_NAMES = {
        "Abbasi", "Adeyemi", "Almeida", "Anderson", "Baptiste", "Bergeron", "Bianchi", "Bose", "Castillo",
        "Chowdhury", "Cohen", "Costa", "Dubois", "Edwards", "Fernandes", "Fraser", "Gill", "Gomez", "Gupta",
        "Hassan", "Henry", "Ibrahim", "Iyer", "Jovanovic", "Kaur", "Khan", "Kim", "Kowalczyk", "Lam", "Leblanc",
        "Li", "Lopez", "MacLeod", "Mahmoud", "Mensah", "Morrison", "Murphy", "Nair", "Novak", "Ogunleye",
        "Okonkwo", "Park", "Pereira", "Popescu", "Qureshi", "Ramos", "Reid", "Sato", "Schmidt", "Shah",
        "Sokolov", "Stewart", "Tan", "Thompson", "Torres", "Vasquez", "Walsh", "Wilson", "Yilmaz", "Zhang"};
    private static final String PLATE_LETTERS = "ABCDEFGHJKLMNPRSTVWXYZ";
    private static final Duration MIN_DURATION = Duration.ofMinutes(30);
    private static final Duration MAX_DURATION = Duration.ofHours(24);
    private static final Duration REFUND_NOTICE = Duration.ofHours(1);
    /** Ended bookings younger than this stay CONFIRMED; the auto-checkout job completes them for real. */
    private static final Duration CHECKOUT_LAG = Duration.ofMinutes(2);
    /** Probability that a booking planned for {@code n} days ahead has already been made. */
    private static final double[] BOOKED_AHEAD = {0.9, 0.7, 0.45, 0.3, 0.2, 0.12, 0.08, 0.05};
    private static final double CANCEL_RATE = 0.07;

    private final PricingService pricing;
    private final ZoneId zone;

    DemoHistoryPlanner(PricingService pricing, ZoneId zone) {
        this.pricing = Objects.requireNonNull(pricing, "pricing");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** The driver population, deterministic for a given {@code today}. Excluded e-mails are never generated. */
    List<Driver> drivers(LocalDate today, Set<String> excludedEmails) {
        Random rnd = new Random(DRIVER_SEED);
        Set<String> used = new HashSet<>(excludedEmails);
        List<Driver> drivers = new ArrayList<>();
        for (Map.Entry<Persona, Integer> entry : POPULATION.entrySet()) {
            Persona persona = entry.getKey();
            for (int i = 0; i < entry.getValue(); i++) {
                String first;
                String last;
                String email;
                do {
                    first = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)];
                    last = LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
                    email = (first + "." + last).toLowerCase(Locale.ROOT) + "@" + persona.domain;
                } while (!used.add(email));
                Instant createdAt = today.minusDays(HISTORY_DAYS + 10 + rnd.nextInt(90))
                        .atTime(9 + rnd.nextInt(10), rnd.nextInt(60)).atZone(zone).toInstant();
                drivers.add(new Driver(uuid(rnd), email, first, last, persona, plate(rnd), paymentMethod(rnd),
                        homeLot(persona, rnd), createdAt));
            }
        }
        return List.copyOf(drivers);
    }

    /**
     * Plans bookings from {@value #HISTORY_DAYS} days ago up to {@value #FUTURE_DAYS} days ahead.
     *
     * @param busy           non-cancelled bookings already in the database, by spot id (they are never overlapped)
     * @param takenReferences booking references already in use
     */
    Plan plan(List<Driver> drivers, List<SpotInfo> spots, Instant now, Map<Long, List<Interval>> busy,
              Set<String> takenReferences) {
        Random rnd = new Random(HISTORY_SEED);
        ReferenceCodeGenerator references = new ReferenceCodeGenerator(rnd);
        Set<String> usedReferences = new HashSet<>(takenReferences);
        LocalDate today = now.atZone(zone).toLocalDate();

        Map<String, List<SpotInfo>> spotsByLot = new LinkedHashMap<>();
        for (SpotInfo spot : spots) {
            spotsByLot.computeIfAbsent(spot.lot(), k -> new ArrayList<>()).add(spot);
        }
        Map<Long, TreeMap<Instant, Instant>> spotCalendar = new HashMap<>();
        busy.forEach((spotId, intervals) -> intervals.forEach(
                interval -> calendar(spotCalendar, spotId).put(interval.start(), interval.end())));
        Map<UUID, TreeMap<Instant, Instant>> driverCalendar = new HashMap<>();
        Map<UUID, Long> favourite = new HashMap<>();
        for (Driver driver : drivers) {
            List<SpotInfo> home = spotsByLot.getOrDefault(driver.homeLot(), spots);
            if (!home.isEmpty()) {
                favourite.put(driver.id(), home.get(rnd.nextInt(home.size())).id());
            }
        }

        List<PlannedBooking> bookings = new ArrayList<>();
        List<PlannedPayment> payments = new ArrayList<>();
        List<Driver> order = new ArrayList<>(drivers);
        for (LocalDate day = today.minusDays(HISTORY_DAYS); !day.isAfter(today.plusDays(FUTURE_DAYS));
             day = day.plusDays(1)) {
            // some days are simply busier than others (weather, exams, events)
            double busyness = 0.85 + 0.25 * rnd.nextDouble();
            DayKind kind = dayKind(day);
            Collections.shuffle(order, rnd);
            for (Driver driver : order) {
                for (Interval window : visits(driver.persona(), kind, day, busyness, rnd)) {
                    PlannedBooking booking = book(driver, window, now, today, spotsByLot, spots, spotCalendar,
                            driverCalendar, favourite.get(driver.id()), references, usedReferences, rnd);
                    if (booking != null) {
                        bookings.add(booking);
                        payments.addAll(paymentsFor(booking));
                    }
                }
            }
        }
        return new Plan(List.copyOf(bookings), List.copyOf(payments));
    }

    private enum DayKind { WEEKDAY, FRIDAY, WEEKEND, HOLIDAY }

    /** Candidate parking windows for one driver on one day (before checking spots and the driver's calendar). */
    private List<Interval> visits(Persona persona, DayKind kind, LocalDate day, double busyness, Random rnd) {
        List<Interval> visits = new ArrayList<>(2);
        double presence = busyness * switch (persona) {
            case RESIDENT -> switch (kind) {
                case WEEKDAY -> 0.9;
                case FRIDAY -> 0.75;
                case WEEKEND -> 0.7;
                case HOLIDAY -> 0.6;
            };
            case COMMUTER -> switch (kind) {
                case WEEKDAY -> 0.82;
                case FRIDAY -> 0.6;
                case WEEKEND -> 0.12;
                case HOLIDAY -> 0.05;
            };
            case FACULTY -> switch (kind) {
                case WEEKDAY -> 0.78;
                case FRIDAY -> 0.5;
                case WEEKEND -> 0.08;
                case HOLIDAY -> 0.03;
            };
            case STAFF -> switch (kind) {
                case WEEKDAY -> 0.93;
                case FRIDAY -> 0.85;
                case WEEKEND -> 0.06;
                case HOLIDAY -> 0.03;
            };
            case VISITOR -> switch (kind) {
                case WEEKDAY, FRIDAY -> 0.32;
                case WEEKEND -> 0.42;
                case HOLIDAY -> 0.3;
            };
        };
        if (rnd.nextDouble() >= presence) {
            return visits;
        }
        switch (persona) {
            // evening arrival, leaves the next morning or afternoon (overnight residence parking, under 24 h)
            case RESIDENT -> visits.add(window(day, at(16, 30, 8, rnd), day.plusDays(1), at(11, 0, 10, rnd)));
            case COMMUTER -> {
                LocalTime start = at(8, 0, 11, rnd);
                visits.add(window(day, start, halfHours(4, 12, rnd)));
                if (rnd.nextDouble() < 0.2) {
                    visits.add(window(day, at(18, 0, 4, rnd), halfHours(2, 6, rnd)));
                }
            }
            case FACULTY -> visits.add(window(day, at(8, 0, 7, rnd), halfHours(10, 19, rnd)));
            case STAFF -> visits.add(window(day, at(7, 0, 6, rnd), halfHours(16, 19, rnd)));
            case VISITOR -> {
                // visitors often book odd lengths, which shows the round-up billing
                Duration length = Duration.ofMinutes(45 + 15L * rnd.nextInt(13));
                visits.add(window(day, at(9, 0, 21, rnd), length));
            }
        }
        return visits;
    }

    private PlannedBooking book(Driver driver, Interval window, Instant now, LocalDate today,
                                Map<String, List<SpotInfo>> spotsByLot, List<SpotInfo> allSpots,
                                Map<Long, TreeMap<Instant, Instant>> spotCalendar,
                                Map<UUID, TreeMap<Instant, Instant>> driverCalendar, Long favourite,
                                ReferenceCodeGenerator references, Set<String> usedReferences, Random rnd) {
        Instant start = window.start();
        Instant end = window.end();
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(MIN_DURATION) < 0 || duration.compareTo(MAX_DURATION) > 0) {
            return null;
        }
        Instant createdAt = start.minus(leadTime(driver.persona(), rnd));
        if (createdAt.isAfter(now)) {
            // not booked yet unless the driver plans ahead
            long daysAhead = ChronoUnit.DAYS.between(today, start.atZone(zone).toLocalDate());
            double booked = daysAhead < BOOKED_AHEAD.length ? BOOKED_AHEAD[(int) Math.max(0, daysAhead)] : 0;
            if (rnd.nextDouble() >= booked) {
                return null;
            }
            createdAt = now.minus(Duration.ofMinutes(5 + rnd.nextInt(36 * 60)));
            if (createdAt.isAfter(start)) {
                createdAt = start.minus(Duration.ofMinutes(5));
            }
        }

        Instant cancelledAt = null;
        if (rnd.nextDouble() < CANCEL_RATE) {
            Instant refundDeadline = start.minus(REFUND_NOTICE);
            if (rnd.nextDouble() < 0.65 && refundDeadline.isAfter(createdAt)) {
                cancelledAt = between(createdAt.plusSeconds(60), refundDeadline, rnd);
            } else {
                Instant from = refundDeadline.isAfter(createdAt) ? refundDeadline : createdAt;
                cancelledAt = between(from.plusSeconds(60), start.plus(Duration.ofMinutes(10)), rnd);
            }
            if (cancelledAt.isAfter(now)) {
                cancelledAt = null; // that cancellation hasn't happened yet
            }
        }

        boolean confirmedNow = cancelledAt == null && end.plus(CHECKOUT_LAG).isAfter(now);
        Long spotId;
        if (cancelledAt != null) {
            spotId = pickSpot(driver, favourite, start, end, false, spotsByLot, allSpots, spotCalendar, rnd, false);
        } else {
            if (overlaps(driverCalendar.get(driver.id()), start, end)) {
                return null;
            }
            spotId = pickSpot(driver, favourite, start, end, confirmedNow, spotsByLot, allSpots, spotCalendar, rnd,
                    true);
        }
        if (spotId == null) {
            return null;
        }
        if (cancelledAt == null) {
            calendar(spotCalendar, spotId).put(start, end);
            driverCalendar.computeIfAbsent(driver.id(), k -> new TreeMap<>()).put(start, end);
        }

        BigDecimal rate = pricing.hourlyRate(driver.type());
        BigDecimal deposit = pricing.deposit(rate);
        BigDecimal total = pricing.total(rate, start, end);
        String reference = references.generateUnique(code -> !usedReferences.add(code));
        BookingStatus status;
        Instant completedAt = null;
        if (cancelledAt != null) {
            status = BookingStatus.CANCELLED;
        } else if (confirmedNow) {
            status = BookingStatus.CONFIRMED;
        } else {
            status = BookingStatus.COMPLETED;
            completedAt = rnd.nextDouble() < 0.75
                    // the auto-checkout job runs every minute
                    ? end.plusSeconds(5 + rnd.nextInt(55))
                    // the driver checked out (checkout is allowed any time after the start)
                    : end.minus(Duration.ofMinutes(rnd.nextInt((int) Math.min(45, duration.toMinutes() - 1))));
        }
        return new PlannedBooking(uuid(rnd), reference, driver, spotId, start, end, status, rate, deposit, total,
                createdAt, cancelledAt, completedAt);
    }

    private List<PlannedPayment> paymentsFor(PlannedBooking booking) {
        List<PlannedPayment> payments = new ArrayList<>(2);
        payments.add(payment(booking, PaymentType.DEPOSIT, booking.depositAmount(), booking.createdAt()));
        if (booking.status() == BookingStatus.COMPLETED) {
            BigDecimal finalCharge = pricing.finalCharge(booking.totalAmount(), booking.depositAmount());
            if (finalCharge.signum() > 0) {
                payments.add(payment(booking, PaymentType.FINAL, finalCharge, booking.completedAt()));
            }
        } else if (booking.status() == BookingStatus.CANCELLED && isRefunded(booking)) {
            payments.add(payment(booking, PaymentType.REFUND, booking.depositAmount(), booking.cancelledAt()));
        }
        return payments;
    }

    /** The application's rule: the deposit is refunded when cancelled at least one hour before the start. */
    static boolean isRefunded(PlannedBooking booking) {
        return booking.cancelledAt() != null && !booking.cancelledAt().isAfter(booking.start().minus(REFUND_NOTICE));
    }

    private static PlannedPayment payment(PlannedBooking booking, PaymentType type, BigDecimal amount, Instant at) {
        // derived from the booking id so payments stay deterministic without consuming the shared RNG
        UUID id = UUID.nameUUIDFromBytes((booking.id() + ":" + type).getBytes(StandardCharsets.UTF_8));
        return new PlannedPayment(id, booking.id(), type, amount, booking.driver().paymentMethod(),
                "SEED-" + booking.referenceCode() + "-" + type, at);
    }

    /**
     * The driver's usual spot if free, else a random free spot in the home lot, else anywhere. A booking that is
     * still confirmed needs a spot that is bookable today; cancelled bookings don't occupy the spot.
     */
    private Long pickSpot(Driver driver, Long favourite, Instant start, Instant end, boolean mustBeBookable,
                          Map<String, List<SpotInfo>> spotsByLot, List<SpotInfo> allSpots,
                          Map<Long, TreeMap<Instant, Instant>> spotCalendar, Random rnd, boolean checkFree) {
        List<SpotInfo> home = spotsByLot.getOrDefault(driver.homeLot(), List.of());
        if (favourite != null && rnd.nextDouble() < 0.7) {
            for (SpotInfo spot : home) {
                if (spot.id() == favourite && usable(spot, start, end, mustBeBookable, spotCalendar, checkFree)) {
                    return spot.id();
                }
            }
        }
        Long chosen = randomFree(home, start, end, mustBeBookable, spotCalendar, rnd, checkFree);
        return chosen != null ? chosen : randomFree(allSpots, start, end, mustBeBookable, spotCalendar, rnd, checkFree);
    }

    private static Long randomFree(Collection<SpotInfo> candidates, Instant start, Instant end, boolean mustBeBookable,
                                   Map<Long, TreeMap<Instant, Instant>> spotCalendar, Random rnd, boolean checkFree) {
        if (candidates.isEmpty()) {
            return null;
        }
        List<SpotInfo> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, rnd);
        for (SpotInfo spot : shuffled) {
            if (usable(spot, start, end, mustBeBookable, spotCalendar, checkFree)) {
                return spot.id();
            }
        }
        return null;
    }

    private static boolean usable(SpotInfo spot, Instant start, Instant end, boolean mustBeBookable,
                                  Map<Long, TreeMap<Instant, Instant>> spotCalendar, boolean checkFree) {
        return (!mustBeBookable || spot.bookable()) && (!checkFree || !overlaps(spotCalendar.get(spot.id()), start, end));
    }

    /** Whether {@code [start, end)} overlaps any interval of a calendar of non-overlapping intervals. */
    static boolean overlaps(TreeMap<Instant, Instant> calendar, Instant start, Instant end) {
        if (calendar == null) {
            return false;
        }
        Map.Entry<Instant, Instant> before = calendar.floorEntry(start);
        if (before != null && before.getValue().isAfter(start)) {
            return true;
        }
        Map.Entry<Instant, Instant> after = calendar.ceilingEntry(start);
        return after != null && after.getKey().isBefore(end);
    }

    private static TreeMap<Instant, Instant> calendar(Map<Long, TreeMap<Instant, Instant>> calendars, long spotId) {
        return calendars.computeIfAbsent(spotId, k -> new TreeMap<>());
    }

    private Duration leadTime(Persona persona, Random rnd) {
        return switch (persona) {
            case RESIDENT -> Duration.ofMinutes(60 + rnd.nextInt(5 * 60));
            case COMMUTER -> Duration.ofMinutes(30 + rnd.nextInt(24 * 60));
            case FACULTY -> Duration.ofMinutes(12 * 60 + rnd.nextInt(60 * 60));
            case STAFF -> Duration.ofMinutes(12 * 60 + rnd.nextInt(36 * 60));
            case VISITOR -> Duration.ofMinutes(10 + rnd.nextInt(5 * 24 * 60));
        };
    }

    private Interval window(LocalDate day, LocalTime start, Duration length) {
        Instant from = day.atTime(start).atZone(zone).toInstant();
        return new Interval(from, from.plus(length));
    }

    private Interval window(LocalDate startDay, LocalTime start, LocalDate endDay, LocalTime end) {
        return new Interval(startDay.atTime(start).atZone(zone).toInstant(), endDay.atTime(end).atZone(zone).toInstant());
    }

    /** A time from {@code hour:minute} in up to {@code steps} further half-hour steps. */
    private static LocalTime at(int hour, int minute, int steps, Random rnd) {
        return LocalTime.of(hour, minute).plusMinutes(30L * rnd.nextInt(steps + 1));
    }

    /** Between {@code min} and {@code max} half hours. */
    private static Duration halfHours(int min, int max, Random rnd) {
        return Duration.ofMinutes(30L * (min + rnd.nextInt(max - min + 1)));
    }

    private static Instant between(Instant from, Instant to, Random rnd) {
        long span = Duration.between(from, to).toSeconds();
        return span <= 0 ? from : from.plusSeconds((long) (rnd.nextDouble() * span));
    }

    /** Weekends plus the Ontario holidays a campus closes for, and the winter closure. */
    static DayKind dayKind(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        if (isHoliday(day)) {
            return DayKind.HOLIDAY;
        }
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return DayKind.WEEKEND;
        }
        return dow == DayOfWeek.FRIDAY ? DayKind.FRIDAY : DayKind.WEEKDAY;
    }

    static boolean isHoliday(LocalDate day) {
        int year = day.getYear();
        if ((day.getMonth() == Month.DECEMBER && day.getDayOfMonth() >= 24)
                || (day.getMonth() == Month.JANUARY && day.getDayOfMonth() == 1)
                || day.equals(LocalDate.of(year, Month.JULY, 1))) {
            return true;
        }
        return day.equals(nthMonday(year, Month.FEBRUARY, 3))              // Family Day
                || day.equals(LocalDate.of(year, Month.MAY, 25)
                        .with(TemporalAdjusters.previous(DayOfWeek.MONDAY))) // Victoria Day
                || day.equals(nthMonday(year, Month.AUGUST, 1))            // Civic Holiday
                || day.equals(nthMonday(year, Month.SEPTEMBER, 1))         // Labour Day
                || day.equals(nthMonday(year, Month.OCTOBER, 2));          // Thanksgiving
    }

    private static LocalDate nthMonday(int year, Month month, int n) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY));
    }

    private static String homeLot(Persona persona, Random rnd) {
        double r = rnd.nextDouble();
        return switch (persona) {
            case RESIDENT -> r < 0.7 ? "B" : "A";
            case COMMUTER -> r < 0.45 ? "A" : r < 0.75 ? "B" : "C";
            case FACULTY -> r < 0.4 ? "A" : r < 0.8 ? "C" : "B";
            case STAFF -> r < 0.4 ? "B" : r < 0.65 ? "A" : "C";
            case VISITOR -> r < 0.4 ? "A" : r < 0.8 ? "C" : "B";
        };
    }

    private static PaymentMethod paymentMethod(Random rnd) {
        double r = rnd.nextDouble();
        return r < 0.5 ? PaymentMethod.CREDIT_CARD : r < 0.8 ? PaymentMethod.DEBIT_CARD : PaymentMethod.PAYPAL;
    }

    /** Ontario-style plate, e.g. {@code CKRT 482}. */
    private static String plate(Random rnd) {
        StringBuilder plate = new StringBuilder(8);
        for (int i = 0; i < 4; i++) {
            plate.append(PLATE_LETTERS.charAt(rnd.nextInt(PLATE_LETTERS.length())));
        }
        return plate.append(' ').append(100 + rnd.nextInt(900)).toString();
    }

    /** A version-4 UUID drawn from the seeded generator. */
    private static UUID uuid(Random rnd) {
        long msb = (rnd.nextLong() & ~0xF000L) | 0x4000L;
        long lsb = (rnd.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    private static Map<Persona, Integer> populationOf(Object... personaCounts) {
        Map<Persona, Integer> population = new LinkedHashMap<>();
        for (int i = 0; i < personaCounts.length; i += 2) {
            population.put((Persona) personaCounts[i], (Integer) personaCounts[i + 1]);
        }
        return Collections.unmodifiableMap(population);
    }

    /** Sorts bookings so inserts take spot row locks in id order, like every other spot-locking path. */
    static List<PlannedBooking> insertOrder(List<PlannedBooking> bookings) {
        List<PlannedBooking> sorted = new ArrayList<>(bookings);
        sorted.sort(Comparator.comparingLong(PlannedBooking::spotId).thenComparing(PlannedBooking::start));
        return sorted;
    }
}
