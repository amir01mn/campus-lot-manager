package io.github.amir01mn.parking.auth;

import io.github.amir01mn.parking.common.ConflictException;
import io.github.amir01mn.parking.user.DirectoryService;
import io.github.amir01mn.parking.user.Role;
import io.github.amir01mn.parking.user.User;
import io.github.amir01mn.parking.user.UserProfileResponse;
import io.github.amir01mn.parking.user.UserRepository;
import io.github.amir01mn.parking.user.UserService;
import io.github.amir01mn.parking.user.UserType;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final DirectoryService directoryService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Clock clock;
    /** Compared against when the e-mail is unknown, so both failure paths cost one BCrypt check. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, UserService userService, DirectoryService directoryService,
                       PasswordEncoder passwordEncoder, TokenService tokenService, Clock clock) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.directoryService = directoryService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    /** Creates a {@link Role#USER} account whose pricing tier comes from the university directory. */
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        UserType type = directoryService.resolveType(email);
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim(), type, Role.USER, clock.instant()));
        log.info("Registered user {} as {}", user.getId(), type);
        return userService.toProfile(user);
    }

    /**
     * Verifies credentials and issues an access token. Read-only: a failed attempt never changes any state.
     *
     * @throws BadCredentialsException (401) for an unknown e-mail or a wrong password, without saying which
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(User.normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            log.info("Failed login attempt");
            throw new BadCredentialsException("Invalid email or password.");
        }
        TokenService.IssuedToken token = tokenService.issue(user.get());
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds(), userService.toProfile(user.get()));
    }
}
