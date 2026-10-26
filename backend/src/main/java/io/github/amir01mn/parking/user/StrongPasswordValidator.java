package io.github.amir01mn.parking.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        List<String> violations = PasswordPolicy.violations(value);
        if (violations.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        violations.forEach(message -> context.buildConstraintViolationWithTemplate(escape(message))
                .addConstraintViolation());
        return false;
    }

    /** Messages are fixed strings, but escape EL/interpolation characters anyway. */
    private static String escape(String message) {
        return message.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
