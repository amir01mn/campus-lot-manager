package io.github.amir01mn.parking.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.amir01mn.parking.booking.BookingRepository;
import io.github.amir01mn.parking.lot.ParkingSpotRepository;
import io.github.amir01mn.parking.user.Role;
import io.github.amir01mn.parking.user.User;
import io.github.amir01mn.parking.user.UserRepository;
import io.github.amir01mn.parking.user.UserType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2030-03-01T14:00:00Z");

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailSender email = mock(EmailSender.class);
    private final NotificationService service = new NotificationService(notifications, users,
            mock(BookingRepository.class), mock(ParkingSpotRepository.class), email,
            Clock.fixed(NOW, ZoneId.of("America/Toronto")));

    @Test
    void bookingOwnerGetsNotificationAndEmail() {
        User owner = new User("owner@example.com", "hash", "O", "Wner", UserType.STUDENT, Role.USER, NOW);
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(owner));

        service.notifyBookingOwner(id, NotificationType.BOOKING_CONFIRMED, "Subject", "Body", null, null);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getRecipient()).isSameAs(owner);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        verify(email).send("owner@example.com", "Subject", "Body");
    }

    @Test
    void deletedOwnerIsSkipped() {
        when(users.findById(any())).thenReturn(Optional.empty());

        service.notifyBookingOwner(UUID.randomUUID(), NotificationType.BOOKING_CONFIRMED, "S", "B", null, null);

        verify(notifications, never()).save(any());
        verify(email, never()).send(any(), any(), any());
    }

    @Test
    void managerAlertsAreInAppOnly() {
        User manager = new User("m@yorku.ca", "hash", "M", "Gr", UserType.STAFF, Role.MANAGER, NOW);
        when(users.findByRoleIn(NotificationService.MANAGER_ROLES)).thenReturn(List.of(manager));

        assertThat(service.notifyManagers(NotificationType.UNAUTHORIZED_PARKING, "msg", null, null)).isEqualTo(1);

        verify(notifications).save(any(Notification.class));
        verify(email, never()).send(any(), any(), any());
    }
}
