package io.github.amir01mn.parking.analytics;

import io.github.amir01mn.parking.booking.BookingStatus;
import io.github.amir01mn.parking.booking.PricingService;
import io.github.amir01mn.parking.common.BusinessRuleException;
import io.github.amir01mn.parking.common.Money;
import io.github.amir01mn.parking.user.UserType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager dashboard analytics. Every figure comes from an aggregate SQL query (nothing is loaded row by row);
 * days are calendar days in the campus time zone.
 */
@Service
public class StatsService {

    static final Duration DEFAULT_RANGE = Duration.ofDays(30);
    static final Duration MAX_RANGE = Duration.ofDays(366);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    private static final String SPOTS_SQL = """
            select count(*) as total, count(*) filter (where occupied) as occupied from parking_spots
            """;

    private static final String BOOKINGS_BY_STATUS_SQL = """
            select status, count(*) as n from bookings
            where start_time >= :from and start_time < :to
            group by status
            """;

    private static final String BOOKINGS_PER_DAY_SQL = """
            select cast(start_time at time zone :tz as date) as day, count(*) as n from bookings
            where start_time >= :from and start_time < :to
            group by 1
            """;

    private static final String REVENUE_BY_TYPE_SQL = """
            select u.user_type,
                   sum(case when p.type = 'REFUND' then -p.amount else p.amount end) as revenue
            from payments p
            join bookings b on b.id = p.booking_id
            join users u on u.id = b.user_id
            where p.status = 'SUCCEEDED' and p.created_at >= :from and p.created_at < :to
            group by u.user_type
            """;

    private static final String LOT_UTILISATION_SQL = """
            select l.id, l.name, coalesce(s.spots, 0) as spots, coalesce(h.booked_seconds, 0) as booked_seconds
            from parking_lots l
            left join (select lot_id, count(*) as spots from parking_spots group by lot_id) s on s.lot_id = l.id
            left join (
                select sp.lot_id,
                       sum(extract(epoch from least(b.end_time, cast(:to as timestamptz))
                                            - greatest(b.start_time, cast(:from as timestamptz)))) as booked_seconds
                from bookings b
                join parking_spots sp on sp.id = b.spot_id
                where b.status <> 'CANCELLED' and b.start_time < :to and b.end_time > :from
                group by sp.lot_id
            ) h on h.lot_id = l.id
            order by l.name
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PricingService pricingService;
    private final Clock clock;

    public StatsService(NamedParameterJdbcTemplate jdbc, PricingService pricingService, Clock clock) {
        this.jdbc = jdbc;
        this.pricingService = pricingService;
        this.clock = clock;
    }

    /** Stats for {@code [from, to)}; {@code to} defaults to now and {@code from} to 30 days before {@code to}. */
    @Transactional(readOnly = true)
    public StatsResponse stats(Instant fromOrNull, Instant toOrNull) {
        Instant to = toOrNull != null ? toOrNull : clock.instant();
        Instant from = fromOrNull != null ? fromOrNull : to.minus(DEFAULT_RANGE);
        if (!to.isAfter(from)) {
            throw new BusinessRuleException("INVALID_TIME_RANGE", "'to' must be after 'from'.");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new BusinessRuleException("RANGE_TOO_LONG", "The range can be at most 366 days.");
        }
        ZoneId zone = clock.getZone();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .addValue("tz", zone.getId());

        long[] spots = jdbc.queryForObject(SPOTS_SQL, Map.of(),
                (rs, i) -> new long[] {rs.getLong("total"), rs.getLong("occupied")});
        long totalSpots = spots == null ? 0 : spots[0];
        long occupiedSpots = spots == null ? 0 : spots[1];

        Map<BookingStatus, Long> byStatus = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            byStatus.put(status, 0L);
        }
        jdbc.query(BOOKINGS_BY_STATUS_SQL, params,
                rs -> {
                    byStatus.put(BookingStatus.valueOf(rs.getString("status")), rs.getLong("n"));
                });
        long bookingsCount = byStatus.values().stream().mapToLong(Long::longValue).sum();

        Map<LocalDate, Long> perDay = new HashMap<>();
        jdbc.query(BOOKINGS_PER_DAY_SQL, params,
                rs -> {
                    perDay.put(rs.getObject("day", LocalDate.class), rs.getLong("n"));
                });
        List<StatsResponse.DailyBookings> bookingsPerDay = new ArrayList<>();
        LocalDate lastDay = to.minusNanos(1).atZone(zone).toLocalDate();
        for (LocalDate day = from.atZone(zone).toLocalDate(); !day.isAfter(lastDay); day = day.plusDays(1)) {
            bookingsPerDay.add(new StatsResponse.DailyBookings(day, perDay.getOrDefault(day, 0L)));
        }

        Map<UserType, BigDecimal> revenueByType = new EnumMap<>(UserType.class);
        for (UserType type : UserType.values()) {
            revenueByType.put(type, Money.ZERO);
        }
        jdbc.query(REVENUE_BY_TYPE_SQL, params,
                rs -> {
                    revenueByType.put(UserType.valueOf(rs.getString("user_type")),
                            Money.of(rs.getBigDecimal("revenue")));
                });
        BigDecimal revenue = Money.of(revenueByType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal rangeHours = BigDecimal.valueOf(Duration.between(from, to).toSeconds())
                .divide(SECONDS_PER_HOUR, 6, RoundingMode.HALF_UP);
        List<StatsResponse.LotUtilisation> lots = jdbc.query(LOT_UTILISATION_SQL, params, (rs, i) -> {
            long lotSpots = rs.getLong("spots");
            BigDecimal booked = rs.getBigDecimal("booked_seconds").divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);
            BigDecimal available = rangeHours.multiply(BigDecimal.valueOf(lotSpots));
            return new StatsResponse.LotUtilisation(rs.getLong("id"), rs.getString("name"), lotSpots, booked,
                    available.setScale(2, RoundingMode.HALF_UP), percent(booked, available));
        });

        return new StatsResponse(from, to, zone.getId(), pricingService.currency(), totalSpots, occupiedSpots,
                percent(BigDecimal.valueOf(occupiedSpots), BigDecimal.valueOf(totalSpots)), bookingsCount, byStatus,
                revenue, bookingsPerDay,
                revenueByType.entrySet().stream()
                        .map(e -> new StatsResponse.UserTypeRevenue(e.getKey(), e.getValue()))
                        .toList(),
                lots);
    }

    /** {@code part / whole} as a percentage with one decimal; 0 when {@code whole} is 0. */
    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
