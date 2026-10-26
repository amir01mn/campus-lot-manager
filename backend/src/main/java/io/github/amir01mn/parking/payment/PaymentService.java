package io.github.amir01mn.parking.payment;

import io.github.amir01mn.parking.booking.Booking;
import io.github.amir01mn.parking.common.BusinessRuleException;
import io.github.amir01mn.parking.common.Money;
import io.github.amir01mn.parking.config.PricingProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Sends money movements through the {@link PaymentGateway} and records them as {@link Payment} rows. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentGateway gateway;
    private final PaymentRepository paymentRepository;
    private final Clock clock;
    private final String currency;

    public PaymentService(PaymentGateway gateway, PaymentRepository paymentRepository, Clock clock,
                          PricingProperties pricingProperties) {
        this.gateway = gateway;
        this.paymentRepository = paymentRepository;
        this.clock = clock;
        this.currency = pricingProperties.currency();
    }

    /**
     * Processes and records a DEPOSIT, FINAL or REFUND of {@code amount} with the booking's payment method.
     * Joins the caller's transaction, so the payment row commits or rolls back with the booking change.
     *
     * @throws BusinessRuleException ({@code PAYMENT_DECLINED}, 422) if the gateway declines; the surrounding
     *                               transaction then rolls back
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment record(Booking booking, PaymentType type, BigDecimal amount) {
        BigDecimal value = Money.of(amount);
        PaymentGateway.GatewayResult result = gateway.process(new PaymentGateway.GatewayRequest(
                booking.getId(), booking.getReferenceCode(), type, value, currency, booking.getPaymentMethod()));
        PaymentStatus status = result.succeeded() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;
        Payment payment = paymentRepository.save(new Payment(booking, type, value, booking.getPaymentMethod(),
                status, result.providerReference(), clock.instant()));
        if (!result.succeeded()) {
            log.warn("{} of {} for booking {} declined: {}", type, value, booking.getReferenceCode(),
                    result.failureReason());
            throw new BusinessRuleException("PAYMENT_DECLINED", "The payment was declined: " + result.failureReason());
        }
        log.debug("Recorded {} of {} for booking {}", type, value, booking.getReferenceCode());
        return payment;
    }

    @Transactional(readOnly = true)
    public List<Payment> forBooking(UUID bookingId) {
        return paymentRepository.findByBookingIdOrderByCreatedAtAsc(bookingId);
    }
}
