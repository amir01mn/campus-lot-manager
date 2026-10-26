package io.github.amir01mn.parking.notification;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    /** Sorting comes from the {@code pageable}. */
    Page<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

    /** Sorting comes from the {@code pageable}. */
    Page<Notification> findByRecipientIdAndReadFalse(UUID recipientId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    boolean existsByRecipientIdIn(Collection<UUID> recipientIds);

    /** Marks every unread notification of the recipient read; returns how many changed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.recipient.id = :recipientId and n.read = false")
    int markAllRead(@Param("recipientId") UUID recipientId);
}
