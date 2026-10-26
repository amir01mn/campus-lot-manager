package io.github.amir01mn.parking.auth;

import io.github.amir01mn.parking.user.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-registration. The pricing tier is not chosen here: it comes from the university directory. */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @StrongPassword String password) {

    public RegisterRequest {
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***]";
    }
}
