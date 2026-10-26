package io.github.amir01mn.parking.payment;

import io.github.amir01mn.parking.booking.Booking;
import io.github.amir01mn.parking.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An immutable money movement attached to a booking. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentType type;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentStatus status;

    @Column(name = "provider_reference", length = 100, updatable = false)
    private String providerReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
        // for JPA
    }

    public Payment(Booking booking, PaymentType type, BigDecimal amount, PaymentMethod method,
                   PaymentStatus status, String providerReference, Instant createdAt) {
        this.booking = Objects.requireNonNull(booking, "booking");
        this.type = Objects.requireNonNull(type, "type");
        this.amount = Money.of(amount);
        if (this.amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.method = Objects.requireNonNull(method, "method");
        this.status = Objects.requireNonNull(status, "status");
        this.providerReference = providerReference;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public PaymentType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Payment other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Payment.class.hashCode();
    }

    @Override
    public String toString() {
        return "Payment[id=" + id + ", type=" + type + ", amount=" + amount + ", status=" + status + "]";
    }
}
