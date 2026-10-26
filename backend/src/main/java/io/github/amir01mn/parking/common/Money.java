package io.github.amir01mn.parking.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Helpers for CAD amounts: always {@link BigDecimal} at scale 2, rounded half-up. */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    public static BigDecimal of(BigDecimal amount) {
        return Objects.requireNonNull(amount, "amount").setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(String amount) {
        return of(new BigDecimal(amount));
    }

    /** Returns {@code amount} or zero, whichever is larger. */
    public static BigDecimal nonNegative(BigDecimal amount) {
        return of(amount.max(BigDecimal.ZERO));
    }
}
