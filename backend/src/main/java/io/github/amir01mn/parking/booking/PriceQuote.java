package io.github.amir01mn.parking.booking;

import java.math.BigDecimal;

/**
 * Price breakdown for a booking window.
 *
 * @param hourlyRate    rate applied (CAD/hour)
 * @param billableHours duration rounded up to whole hours
 * @param total         {@code hourlyRate * billableHours}
 * @param deposit       one hour at {@code hourlyRate}, charged up front
 * @param finalCharge   {@code max(0, total - deposit)}, charged at checkout
 * @param currency      ISO 4217 code
 */
public record PriceQuote(
        BigDecimal hourlyRate,
        long billableHours,
        BigDecimal total,
        BigDecimal deposit,
        BigDecimal finalCharge,
        String currency) {
}
