package io.github.amir01mn.parking.user;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @StrongPassword String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
