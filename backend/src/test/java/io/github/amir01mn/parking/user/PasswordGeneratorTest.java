package io.github.amir01mn.parking.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordGeneratorTest {

    private final PasswordGenerator generator = new PasswordGenerator();

    @Test
    void generatedPasswordsMeetThePolicyAndDiffer() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String password = generator.generate();
            assertThat(password).hasSize(PasswordGenerator.LENGTH);
            assertThat(PasswordPolicy.violations(password)).isEmpty();
            assertThat(password).doesNotContainAnyWhitespaces().doesNotContain("0", "O", "1", "l", "I");
            seen.add(password);
        }
        assertThat(seen).hasSize(500);
    }
}
