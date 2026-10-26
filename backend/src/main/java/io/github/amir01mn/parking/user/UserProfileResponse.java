package io.github.amir01mn.parking.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public view of an account, including the hourly rate that applies to it. */
public record UserProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserType userType,
        Role role,
        BigDecimal hourlyRate,
        String currency,
        Instant createdAt) {

    public static UserProfileResponse of(User user, BigDecimal hourlyRate, String currency) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getUserType(), user.getRole(), hourlyRate, currency, user.getCreatedAt());
    }
}
