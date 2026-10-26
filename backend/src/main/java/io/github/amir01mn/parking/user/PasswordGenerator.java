package io.github.amir01mn.parking.user;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Generates one-time passwords that satisfy {@link PasswordPolicy}: {@value #LENGTH} characters from SecureRandom
 * with at least one upper-case letter, lower-case letter, digit and special character. Look-alike characters
 * (0/O, 1/l/I) are excluded so the password can be read out or typed reliably.
 */
@Component
public class PasswordGenerator {

    static final int LENGTH = 16;

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!#$%&*+-=?@^_";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGITS));
        chars.add(pick(SPECIAL));
        while (chars.size() < LENGTH) {
            chars.add(pick(ALL));
        }
        Collections.shuffle(chars, random);
        StringBuilder password = new StringBuilder(LENGTH);
        chars.forEach(password::append);
        String result = password.toString();
        if (!PasswordPolicy.isAcceptable(result)) {
            throw new IllegalStateException("Generated password violates the password policy");
        }
        return result;
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
