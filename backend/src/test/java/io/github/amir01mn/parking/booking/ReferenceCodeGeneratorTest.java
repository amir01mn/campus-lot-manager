package io.github.amir01mn.parking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReferenceCodeGeneratorTest {

    private final ReferenceCodeGenerator generator = new ReferenceCodeGenerator();

    @Test
    void codesMatchTheDatabaseCheck() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.random()).matches("^CP-[A-Z0-9]{6}$").doesNotContain("0", "O", "1", "I", "L");
        }
    }

    @Test
    void retriesOnCollision() {
        Set<String> seen = new HashSet<>();
        AtomicInteger calls = new AtomicInteger();
        String code = generator.generateUnique(candidate -> {
            seen.add(candidate);
            return calls.incrementAndGet() < 3; // first two candidates are "taken"
        });
        assertThat(calls).hasValue(3);
        assertThat(seen).contains(code);
    }

    @Test
    void givesUpAfterTooManyCollisions() {
        assertThatThrownBy(() -> generator.generateUnique(candidate -> true))
                .isInstanceOf(IllegalStateException.class);
    }
}
