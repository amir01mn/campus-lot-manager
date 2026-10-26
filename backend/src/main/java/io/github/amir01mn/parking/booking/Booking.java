package io.github.amir01mn.parking.booking;

import io.github.amir01mn.parking.common.Money;
import io.github.amir01mn.parking.lot.ParkingSpot;
import io.github.amir01mn.parking.payment.PaymentMethod;
import io.github.amir01mn.parking.user.User;
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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A reservation of one spot for the half-open interval {@code [startTime, endTime)}. */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference_code", nullable = false, unique = true, length = 12)
    private String referenceCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spot_id", nullable = false)
    private ParkingSpot spot;

    @Column(name = "licence_plate", nullable = false, length = 10)
    private String licencePlate;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "overstay_alerted_at")
    private Instant overstayAlertedAt;

    @Version
    private long version;

    protected Booking() {
        // for JPA
    }

    public Booking(String referenceCode, User user, ParkingSpot spot, String licencePlate,
                   Instant startTime, Instant endTime, PaymentMethod paymentMethod,
                   BigDecimal hourlyRate, BigDecimal depositAmount, BigDecimal totalAmount, Instant createdAt) {
        this.referenceCode = Objects.requireNonNull(referenceCode, "referenceCode");
        this.user = Objects.requireNonNull(user, "user");
        this.spot = Objects.requireNonNull(spot, "spot");
        this.licencePlate = Objects.requireNonNull(licencePlate, "licencePlate");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod");
        this.hourlyRate = Money.of(hourlyRate);
        this.depositAmount = Money.of(depositAmount);
        this.totalAmount = Money.of(totalAmount);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = BookingStatus.CONFIRMED;
    }

    public boolean isConfirmed() {
        return status == BookingStatus.CONFIRMED;
    }

    /** Moves the end time later and stores the recalculated total. */
    public void extendTo(Instant newEnd, BigDecimal newTotal) {
        requireConfirmed();
        if (!newEnd.isAfter(endTime)) {
            throw new IllegalArgumentException("newEnd must be after the current end time");
        }
        this.endTime = newEnd;
        this.totalAmount = Money.of(newTotal);
    }

    public void cancel(Instant at) {
        requireConfirmed();
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = Objects.requireNonNull(at, "at");
    }

    public void complete(Instant at) {
        requireConfirmed();
        this.status = BookingStatus.COMPLETED;
        this.completedAt = Objects.requireNonNull(at, "at");
    }

    /** Records that an overstay alert was raised for this booking (at most once). */
    public void markOverstayAlerted(Instant at) {
        if (overstayAlertedAt != null) {
            throw new IllegalStateException("Overstay already alerted for booking " + referenceCode);
        }
        this.overstayAlertedAt = Objects.requireNonNull(at, "at");
    }

    private void requireConfirmed() {
        if (status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking " + referenceCode + " is " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public User getUser() {
        return user;
    }

    public ParkingSpot getSpot() {
        return spot;
    }

    public String getLicencePlate() {
        return licencePlate;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getOverstayAlertedAt() {
        return overstayAlertedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Booking other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Booking.class.hashCode();
    }

    @Override
    public String toString() {
        return "Booking[id=" + id + ", ref=" + referenceCode + ", status=" + status + "]";
    }
}
