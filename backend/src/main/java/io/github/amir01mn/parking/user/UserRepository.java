package io.github.amir01mn.parking.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** {@code email} must already be normalised with {@link User#normalizeEmail(String)}. */
    Optional<User> findByEmail(String email);

    /** {@code email} must already be normalised with {@link User#normalizeEmail(String)}. */
    boolean existsByEmail(String email);

    List<User> findByRoleIn(List<Role> roles);

    Page<User> findByRole(Role role, Pageable pageable);
}
