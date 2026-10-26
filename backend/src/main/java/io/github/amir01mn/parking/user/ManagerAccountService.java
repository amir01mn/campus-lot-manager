package io.github.amir01mn.parking.user;

import io.github.amir01mn.parking.common.ConflictException;
import io.github.amir01mn.parking.common.PageResponse;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Super-manager administration of manager accounts. */
@Service
public class ManagerAccountService {

    private static final Logger log = LoggerFactory.getLogger(ManagerAccountService.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final DirectoryService directoryService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final Clock clock;

    public ManagerAccountService(UserRepository userRepository, UserService userService,
                                 DirectoryService directoryService, PasswordEncoder passwordEncoder,
                                 PasswordGenerator passwordGenerator, Clock clock) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.directoryService = directoryService;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.clock = clock;
    }

    /**
     * Creates a MANAGER with a generated password. The pricing tier comes from the university directory; e-mails not
     * listed there get STAFF (managers are campus staff), not VISITOR. The password is never logged.
     */
    @Transactional
    public CreatedManagerResponse createManager(CreateManagerRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        UserType type = directoryService.resolveType(email);
        if (type == UserType.VISITOR) {
            type = UserType.STAFF;
        }
        String password = passwordGenerator.generate();
        User manager = userRepository.save(new User(email, passwordEncoder.encode(password),
                request.firstName().trim(), request.lastName().trim(), type, Role.MANAGER, clock.instant()));
        log.info("Created manager {} ({}, {})", manager.getId(), email, type);
        return new CreatedManagerResponse(userService.toProfile(manager), password);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> listManagers(int page, int size) {
        return PageResponse.of(userRepository.findByRole(Role.MANAGER,
                        PageRequest.of(page, size, Sort.by("lastName", "firstName", "email"))),
                userService::toProfile);
    }
}
