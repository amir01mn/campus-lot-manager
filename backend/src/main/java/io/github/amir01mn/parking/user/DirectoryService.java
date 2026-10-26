package io.github.amir01mn.parking.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Determines a user's pricing tier from the university directory. */
@Service
public class DirectoryService {

    private final UniversityDirectoryRepository directory;

    public DirectoryService(UniversityDirectoryRepository directory) {
        this.directory = directory;
    }

    /** Directory type for the (normalised) e-mail, or {@link UserType#VISITOR} if it is not listed. */
    @Transactional(readOnly = true)
    public UserType resolveType(String email) {
        return directory.findByEmail(User.normalizeEmail(email))
                .map(DirectoryEntry::getUserType)
                .orElse(UserType.VISITOR);
    }
}
