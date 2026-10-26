package io.github.amir01mn.parking.auth;

import io.github.amir01mn.parking.user.User;
import io.github.amir01mn.parking.user.UserService;
import java.util.UUID;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves the caller from the JWT in the security context (token subject = user id). */
@Component
public class CurrentUser {

    private final UserService userService;

    public CurrentUser(UserService userService) {
        this.userService = userService;
    }

    /** The caller's user id; throws an {@code AuthenticationException} (401) when there is no valid token. */
    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new InsufficientAuthenticationException("A bearer token is required");
        }
        try {
            return UUID.fromString(token.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InsufficientAuthenticationException("Token subject is not a user id", ex);
        }
    }

    /** The caller's account, loaded from the database. */
    public User user() {
        return userService.require(id());
    }
}
