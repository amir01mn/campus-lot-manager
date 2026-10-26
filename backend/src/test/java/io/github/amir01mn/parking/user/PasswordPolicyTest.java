package io.github.amir01mn.parking.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Demo#2026", "Secret#123", "Aa1!aaaa", "Ünïcødé1?", "With Space1!"})
    void acceptsPasswordsMeetingEveryRule(String password) {
        assertThat(PasswordPolicy.violations(password)).isEmpty();
        assertThat(PasswordPolicy.isAcceptable(password)).isTrue();
    }

    @Test
    void reportsEachBrokenRule() {
        assertThat(PasswordPolicy.violations("Aa1!aaa")).containsExactly("must be at least 8 characters long");
        assertThat(PasswordPolicy.violations("aa1!aaaa")).containsExactly("must contain an upper-case letter");
        assertThat(PasswordPolicy.violations("AA1!AAAA")).containsExactly("must contain a lower-case letter");
        assertThat(PasswordPolicy.violations("Aab!aaaa")).containsExactly("must contain a digit");
        assertThat(PasswordPolicy.violations("Aa1aaaaa")).containsExactly("must contain a special character");
        assertThat(PasswordPolicy.violations("")).hasSize(5);
        assertThat(PasswordPolicy.violations(null)).containsExactly("must not be null");
    }

    @Test
    void whitespaceIsNotASpecialCharacter() {
        assertThat(PasswordPolicy.violations("Aa1 aaaa")).containsExactly("must contain a special character");
    }

    @Test
    void rejectsPasswordsBcryptWouldTruncate() {
        String seventyTwoBytes = "Aa1!" + "a".repeat(68);
        assertThat(PasswordPolicy.violations(seventyTwoBytes)).isEmpty();
        assertThat(PasswordPolicy.violations(seventyTwoBytes + "a")).containsExactly("must be at most 72 bytes long");
    }
}
