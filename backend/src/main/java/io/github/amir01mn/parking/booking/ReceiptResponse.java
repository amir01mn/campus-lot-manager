package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.payment.PaymentResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * Booking summary plus every payment.
 *
 * @param totalCharged  succeeded DEPOSIT + FINAL payments
 * @param totalRefunded succeeded REFUND payments
 * @param netPaid       {@code totalCharged - totalRefunded}
 */
public record ReceiptResponse(
        BookingResponse booking,
        String customerName,
        String customerEmail,
        List<PaymentResponse> payments,
        BigDecimal totalCharged,
        BigDecimal totalRefunded,
        BigDecimal netPaid,
        String currency) {
}
