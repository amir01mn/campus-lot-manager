package io.github.amir01mn.parking.payment;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Moves money with an external provider. The only implementation is {@link SimulatedPaymentGateway}; a real
 * provider (e.g. Stripe) would implement this interface and replace it.
 */
public interface PaymentGateway {

    /** Charges ({@code DEPOSIT}, {@code FINAL}) or refunds ({@code REFUND}) {@code amount}. */
    GatewayResult process(GatewayRequest request);

    /**
     * @param bookingReference human-readable booking code, used as the provider-side description
     * @param amount           positive amount in {@code currency}
     */
    record GatewayRequest(UUID bookingId, String bookingReference, PaymentType type, BigDecimal amount,
                          String currency, PaymentMethod method) {

        public GatewayRequest {
            Objects.requireNonNull(bookingId, "bookingId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(method, "method");
        }
    }

    /**
     * @param providerReference provider transaction id (present on success)
     * @param failureReason     why the provider declined (present on failure)
     */
    record GatewayResult(boolean succeeded, String providerReference, String failureReason) {

        public static GatewayResult success(String providerReference) {
            return new GatewayResult(true, providerReference, null);
        }

        public static GatewayResult failure(String reason) {
            return new GatewayResult(false, null, reason);
        }
    }
}
