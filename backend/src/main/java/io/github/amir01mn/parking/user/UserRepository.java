package io.github.amir01mn.parking.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** {@code email} must already be normalised with {@link User#normalizeEmail(String)}. */
    Optional<User> findByEmail(String email);

    /** {@code email} must already be normalised with {@link User#normalizeEmail(String)}. */
    boolean existsByEmail(String email);

    /** Loads the user with a row lock ({@code SELECT ... FOR UPDATE}); serialises booking writes per user. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    List<User> findByRoleIn(List<Role> roles);

    Page<User> findByRole(Role role, Pageable pageable);
}
