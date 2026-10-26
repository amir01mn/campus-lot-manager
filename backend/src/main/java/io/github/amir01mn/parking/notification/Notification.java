package io.github.amir01mn.parking.notification;

import io.github.amir01mn.parking.booking.Booking;
import io.github.amir01mn.parking.lot.ParkingSpot;
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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An in-app message to one user; {@code booking} and {@code spot} are optional context links. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private NotificationType type;

    @Column(nullable = false, length = 500, updatable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", updatable = false)
    private ParkingSpot spot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // for JPA
    }

    public Notification(User recipient, NotificationType type, String message,
                        Booking booking, ParkingSpot spot, Instant createdAt) {
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.type = Objects.requireNonNull(type, "type");
        this.message = Objects.requireNonNull(message, "message");
        this.booking = booking;
        this.spot = spot;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void markRead() {
        this.read = true;
    }

    public UUID getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public Booking getBooking() {
        return booking;
    }

    public ParkingSpot getSpot() {
        return spot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notification other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Notification.class.hashCode();
    }

    @Override
    public String toString() {
        return "Notification[id=" + id + ", type=" + type + ", read=" + read + "]";
    }
}
