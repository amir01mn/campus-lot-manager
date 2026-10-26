package io.github.amir01mn.parking.booking;

import java.security.SecureRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * Human-friendly booking references {@code CP-XXXXXX} (matching the DB check {@code ^CP-[A-Z0-9]{6}$}).
 * The alphabet omits look-alike characters (0/O, 1/I/L) so codes are easy to read out.
 */
@Component
public class ReferenceCodeGenerator {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int LENGTH = 6;
    static final int MAX_ATTEMPTS = 10;

    private final RandomGenerator random;

    public ReferenceCodeGenerator() {
        this(new SecureRandom());
    }

    ReferenceCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    public String random() {
        StringBuilder code = new StringBuilder("CP-");
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * A code for which {@code taken} is false, retrying on collision. With 31^6 (~887M) codes a retry is rare;
     * the unique constraint on {@code bookings.reference_code} remains the backstop.
     */
    public String generateUnique(Predicate<String> taken) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = random();
            if (!taken.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique booking reference after "
                + MAX_ATTEMPTS + " attempts");
    }
}
