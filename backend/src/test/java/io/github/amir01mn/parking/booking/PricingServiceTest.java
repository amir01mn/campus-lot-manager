package io.github.amir01mn.parking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.amir01mn.parking.config.PricingProperties;
import io.github.amir01mn.parking.user.UserType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingServiceTest {

    private static final Instant START = Instant.parse("2030-01-15T15:00:00Z");

    private final PricingService pricing = new PricingService(new PricingProperties("CAD",
            new PricingProperties.Rates(new BigDecimal("5.00"), new BigDecimal("8.00"),
                    new BigDecimal("10.00"), new BigDecimal("15.00"))));

    @ParameterizedTest(name = "{0} -> {1} billable hours")
    @CsvSource({
            "PT30M, 1",
            "PT59M59S, 1",
            "PT1H, 1",
            "PT1H0.001S, 2",
            "PT1H1M, 2",
            "PT1H59M, 2",
            "PT2H, 2",
            "PT23H1M, 24",
            "PT24H, 24",
            "PT1S, 1"
    })
    void billableHoursRoundUpToWholeHours(Duration duration, long expectedHours) {
        assertThat(pricing.billableHours(START, START.plus(duration))).isEqualTo(expectedHours);
    }

    @Test
    void rejectsEmptyOrInvertedInterval() {
        assertThatThrownBy(() -> pricing.billableHours(START, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pricing.billableHours(START, START.minusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"STUDENT, 5.00", "FACULTY, 8.00", "STAFF, 10.00", "VISITOR, 15.00"})
    void hourlyRateComesFromConfiguration(UserType type, BigDecimal expected) {
        assertThat(pricing.hourlyRate(type)).isEqualTo(expected);
    }

    @Test
    void ratesAreNormalisedToTwoDecimals() {
        PricingService service = new PricingService(new PricingProperties("CAD",
                new PricingProperties.Rates(new BigDecimal("5"), new BigDecimal("8"),
                        new BigDecimal("10"), new BigDecimal("15"))));

        assertThat(service.hourlyRate(UserType.STUDENT)).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void exactlyOneHour() {
        PriceQuote quote = pricing.quote(UserType.STUDENT, START, START.plus(Duration.ofHours(1)));

        assertThat(quote).isEqualTo(new PriceQuote(new BigDecimal("5.00"), 1, new BigDecimal("5.00"),
                new BigDecimal("5.00"), new BigDecimal("0.00"), "CAD"));
    }

    @Test
    void sixtyOneMinutesIsChargedAsTwoHours() {
        PriceQuote quote = pricing.quote(UserType.FACULTY, START, START.plus(Duration.ofMinutes(61)));

        assertThat(quote.billableHours()).isEqualTo(2);
        assertThat(quote.total()).isEqualTo(new BigDecimal("16.00"));
        assertThat(quote.deposit()).isEqualTo(new BigDecimal("8.00"));
        assertThat(quote.finalCharge()).isEqualTo(new BigDecimal("8.00"));
    }

    @Test
    void thirtyMinutesIsChargedAsOneHourNotFree() {
        PriceQuote quote = pricing.quote(UserType.VISITOR, START, START.plus(Duration.ofMinutes(30)));

        assertThat(quote.billableHours()).isEqualTo(1);
        assertThat(quote.total()).isEqualTo(new BigDecimal("15.00"));
        assertThat(quote.deposit()).isEqualTo(new BigDecimal("15.00"));
        assertThat(quote.finalCharge()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void twentyFourHours() {
        PriceQuote quote = pricing.quote(UserType.STAFF, START, START.plus(Duration.ofHours(24)));

        assertThat(quote.billableHours()).isEqualTo(24);
        assertThat(quote.total()).isEqualTo(new BigDecimal("240.00"));
        assertThat(quote.deposit()).isEqualTo(new BigDecimal("10.00"));
        assertThat(quote.finalCharge()).isEqualTo(new BigDecimal("230.00"));
    }

    @Test
    void totalUsesSnapshotRate() {
        assertThat(pricing.total(new BigDecimal("7.25"), START, START.plus(Duration.ofMinutes(150))))
                .isEqualTo(new BigDecimal("21.75"));
    }

    @Test
    void finalChargeIsNeverNegative() {
        assertThat(pricing.finalCharge(new BigDecimal("5.00"), new BigDecimal("8.00")))
                .isEqualTo(new BigDecimal("0.00"));
        assertThat(pricing.finalCharge(new BigDecimal("20.00"), new BigDecimal("5.00")))
                .isEqualTo(new BigDecimal("15.00"));
    }

    @Test
    void depositIsOneHourAtRate() {
        assertThat(pricing.deposit(new BigDecimal("8"))).isEqualTo(new BigDecimal("8.00"));
    }
}
