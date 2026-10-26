package io.github.amir01mn.parking.user;

import io.github.amir01mn.parking.config.BootstrapProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the super manager from {@code app.bootstrap.super-manager.*} on startup if no account with that e-mail
 * exists. Idempotent; an existing account is never modified (so a changed password survives restarts).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SuperManagerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperManagerBootstrap.class);

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public SuperManagerBootstrap(BootstrapProperties properties, UserRepository userRepository,
                                 PasswordEncoder passwordEncoder, Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        BootstrapProperties.SuperManager config = properties.superManager();
        if (config == null || !config.isConfigured()) {
            log.warn("Super manager bootstrap skipped: app.bootstrap.super-manager.email/password are not set");
            return;
        }
        String email = User.normalizeEmail(config.email());
        if (userRepository.existsByEmail(email)) {
            log.debug("Super manager {} already exists", email);
            return;
        }
        if (!PasswordPolicy.isAcceptable(config.password())) {
            log.warn("The configured super manager password does not meet the password policy; change it after "
                    + "signing in");
        }
        User superManager = userRepository.save(new User(email, passwordEncoder.encode(config.password()),
                config.firstName(), config.lastName(), UserType.STAFF, Role.SUPER_MANAGER, clock.instant()));
        log.info("Created super manager {} ({})", email, superManager.getId());
    }
}
