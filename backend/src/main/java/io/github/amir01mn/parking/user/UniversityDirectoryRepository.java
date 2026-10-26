package io.github.amir01mn.parking.user;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Read-only access to the university directory (keyed by normalised e-mail). */
public interface UniversityDirectoryRepository extends Repository<DirectoryEntry, String> {

    Optional<DirectoryEntry> findByEmail(String email);

    long count();
}
