package io.github.amir01mn.parking.auth;

import io.github.amir01mn.parking.user.UserProfileResponse;

/**
 * Successful login.
 *
 * @param accessToken signed JWT to send as {@code Authorization: Bearer <token>}
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   token lifetime in seconds
 * @param user        the signed-in user's profile
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserProfileResponse user) {
}
