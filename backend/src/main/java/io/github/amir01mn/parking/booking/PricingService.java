package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.common.Money;
import io.github.amir01mn.parking.config.PricingProperties;
import io.github.amir01mn.parking.user.UserType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Pure pricing rules. Billable hours are the booking duration rounded <em>up</em> to the next whole hour
 * (the legacy system truncated, which made sub-hour bookings free).
 */
@Service
public class PricingService {

    private final PricingProperties properties;

    public PricingService(PricingProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /** ISO 4217 currency of every amount. */
    public String currency() {
        return properties.currency();
    }

    public BigDecimal hourlyRate(UserType type) {
        return Money.of(properties.rates().forType(Objects.requireNonNull(type, "type")));
    }

    /** Whole hours charged for {@code [start, end)}; any started hour counts as a full hour. */
    public long billableHours(Instant start, Instant end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
        Duration duration = Duration.between(start, end);
        long wholeHours = duration.toHours();
        return duration.minusHours(wholeHours).isZero() ? wholeHours : wholeHours + 1;
    }

    public BigDecimal total(BigDecimal hourlyRate, Instant start, Instant end) {
        return Money.of(hourlyRate.multiply(BigDecimal.valueOf(billableHours(start, end))));
    }

    /** Deposit is one hour at the given rate. */
    public BigDecimal deposit(BigDecimal hourlyRate) {
        return Money.of(hourlyRate);
    }

    /** Amount still owed at checkout; never negative. */
    public BigDecimal finalCharge(BigDecimal total, BigDecimal deposit) {
        return Money.nonNegative(total.subtract(deposit));
    }

    public PriceQuote quote(UserType type, Instant start, Instant end) {
        BigDecimal rate = hourlyRate(type);
        long hours = billableHours(start, end);
        BigDecimal total = total(rate, start, end);
        BigDecimal deposit = deposit(rate);
        return new PriceQuote(rate, hours, total, deposit, finalCharge(total, deposit), properties.currency());
    }
}
