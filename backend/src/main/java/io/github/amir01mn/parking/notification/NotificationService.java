package io.github.amir01mn.parking.notification;

import io.github.amir01mn.parking.booking.Booking;
import io.github.amir01mn.parking.booking.BookingRepository;
import io.github.amir01mn.parking.common.NotFoundException;
import io.github.amir01mn.parking.common.PageResponse;
import io.github.amir01mn.parking.lot.ParkingSpot;
import io.github.amir01mn.parking.lot.ParkingSpotRepository;
import io.github.amir01mn.parking.user.Role;
import io.github.amir01mn.parking.user.User;
import io.github.amir01mn.parking.user.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and reads in-app notifications. Users only ever see and change their own: every query is scoped by the
 * recipient id, and another user's notification id answers 404.
 */
@Service
public class NotificationService {

    static final List<Role> MANAGER_ROLES = List.of(Role.MANAGER, Role.SUPER_MANAGER);

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ParkingSpotRepository spotRepository;
    private final EmailSender emailSender;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
                               BookingRepository bookingRepository, ParkingSpotRepository spotRepository,
                               EmailSender emailSender, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.spotRepository = spotRepository;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Writes (called by the event listeners inside their own REQUIRES_NEW transaction)

    /** Notifies a booking's owner in-app and by (logged) e-mail. A deleted account is skipped with a warning. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyBookingOwner(UUID userId, NotificationType type, String subject, String message,
                                   UUID bookingId, Long spotId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Not notifying user {} about {}: the account no longer exists", userId, type);
            return;
        }
        notificationRepository.save(new Notification(user, type, message, bookingRef(bookingId), spotRef(spotId),
                clock.instant()));
        emailSender.send(user.getEmail(), subject, message);
    }

    /** Notifies every MANAGER and SUPER_MANAGER in-app; returns the number of recipients. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int notifyManagers(NotificationType type, String message, UUID bookingId, Long spotId) {
        List<User> managers = userRepository.findByRoleIn(MANAGER_ROLES);
        Booking booking = bookingRef(bookingId);
        ParkingSpot spot = spotRef(spotId);
        for (User manager : managers) {
            notificationRepository.save(new Notification(manager, type, message, booking, spot, clock.instant()));
        }
        log.info("{} sent to {} manager(s): {}", type, managers.size(), message);
        return managers.size();
    }

    private Booking bookingRef(UUID bookingId) {
        return bookingId == null ? null : bookingRepository.getReferenceById(bookingId);
    }

    private ParkingSpot spotRef(Long spotId) {
        return spotId == null ? null : spotRepository.getReferenceById(spotId);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Reads and read-state changes for the signed-in user

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalse(userId, pageable)
                : notificationRepository.findByRecipientId(userId, pageable);
        return PageResponse.of(result, NotificationResponse::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    /** Idempotent. Another user's notification is reported as not found. */
    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> NotFoundException.of("Notification", notificationId));
        notification.markRead();
        return NotificationResponse.of(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }
}
