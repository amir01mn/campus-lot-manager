package io.github.amir01mn.parking.user;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Password rules: at least {@value #MIN_LENGTH} characters with an upper-case letter, a lower-case letter, a digit
 * and a special character (anything that is not a letter, digit or whitespace). At most {@value #MAX_BYTES} UTF-8
 * bytes, because BCrypt ignores everything beyond that.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** Human-readable rule violations; empty when the password is acceptable. */
    public static List<String> violations(String password) {
        List<String> problems = new ArrayList<>();
        if (password == null) {
            problems.add("must not be null");
            return problems;
        }
        if (password.length() < MIN_LENGTH) {
            problems.add("must be at least " + MIN_LENGTH + " characters long");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            problems.add("must be at most " + MAX_BYTES + " bytes long");
        }
        if (password.codePoints().noneMatch(Character::isUpperCase)) {
            problems.add("must contain an upper-case letter");
        }
        if (password.codePoints().noneMatch(Character::isLowerCase)) {
            problems.add("must contain a lower-case letter");
        }
        if (password.codePoints().noneMatch(Character::isDigit)) {
            problems.add("must contain a digit");
        }
        if (password.codePoints().noneMatch(PasswordPolicy::isSpecial)) {
            problems.add("must contain a special character");
        }
        return problems;
    }

    public static boolean isAcceptable(String password) {
        return violations(password).isEmpty();
    }

    private static boolean isSpecial(int codePoint) {
        return !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint);
    }
}
