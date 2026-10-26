package io.github.amir01mn.parking.user;

/**
 * A new manager account and its generated password. The password is returned exactly once (here), is never logged
 * or stored in plain text, and should be changed after the first sign-in.
 */
public record CreatedManagerResponse(UserProfileResponse manager, String temporaryPassword) {

    @Override
    public String toString() {
        return "CreatedManagerResponse[manager=" + manager + ", temporaryPassword=***]";
    }
}
