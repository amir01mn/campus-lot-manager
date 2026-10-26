package io.github.amir01mn.parking.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        PaymentType type,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String providerReference,
        Instant createdAt) {

    public static PaymentResponse of(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getType(), payment.getAmount(), payment.getMethod(),
                payment.getStatus(), payment.getProviderReference(), payment.getCreatedAt());
    }
}
