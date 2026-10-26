package io.github.amir01mn.parking.payment;

import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stand-in payment provider: every request succeeds and gets a {@code SIM-} transaction reference. */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    @Override
    public GatewayResult process(GatewayRequest request) {
        String reference = "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                .toUpperCase(Locale.ROOT);
        log.info("[simulated gateway] {} {} {} via {} for booking {} -> {}", request.type(), request.amount(),
                request.currency(), request.method(), request.bookingReference(), reference);
        return GatewayResult.success(reference);
    }
}
