package io.github.amir01mn.parking.notification;

import java.time.Instant;
import java.util.UUID;

/** A notification as returned by the API; {@code bookingId} and {@code spotId} are optional context links. */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String message,
        boolean read,
        UUID bookingId,
        Long spotId,
        Instant createdAt) {

    public static NotificationResponse of(Notification n) {
        // getId() on a lazy proxy does not initialise it
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.isRead(),
                n.getBooking() == null ? null : n.getBooking().getId(),
                n.getSpot() == null ? null : n.getSpot().getId(),
                n.getCreatedAt());
    }

    /** Body of {@code GET /api/notifications/unread-count}. */
    public record UnreadCount(long count) {
    }

    /** Body of {@code POST /api/notifications/read-all}. */
    public record MarkedRead(int updated) {
    }
}
