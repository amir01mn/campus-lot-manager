package io.github.amir01mn.parking.user;

import io.github.amir01mn.parking.booking.PricingService;
import io.github.amir01mn.parking.common.BusinessRuleException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PricingService pricingService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       PricingService pricingService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.pricingService = pricingService;
    }

    /**
     * The account behind an access token. A valid token for a deleted account is treated as unauthenticated.
     */
    @Transactional(readOnly = true)
    public User require(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new InsufficientAuthenticationException("Account no longer exists"));
    }

    /** Like {@link #require(UUID)} but takes a row lock held until the caller's transaction ends. */
    @Transactional(propagation = Propagation.MANDATORY)
    public User requireForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InsufficientAuthenticationException("Account no longer exists"));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(UUID userId) {
        return toProfile(require(userId));
    }

    public UserProfileResponse toProfile(User user) {
        return UserProfileResponse.of(user, pricingService.hourlyRate(user.getUserType()), pricingService.currency());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = require(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("CURRENT_PASSWORD_INCORRECT", "The current password is incorrect.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessRuleException("PASSWORD_UNCHANGED",
                    "The new password must differ from the current password.");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        log.info("Password changed for user {}", user.getId());
    }
}
