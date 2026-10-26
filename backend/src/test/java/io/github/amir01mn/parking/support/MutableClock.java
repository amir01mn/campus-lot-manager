package io.github.amir01mn.parking.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test clock: follows the system clock until {@link #setInstant(Instant)} pins it, then only moves when told to.
 * Views created by {@link #withZone(ZoneId)} share the same time.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private final AtomicReference<Instant> pinned;

    public MutableClock(ZoneId zone) {
        this(zone, new AtomicReference<>());
    }

    private MutableClock(ZoneId zone, AtomicReference<Instant> pinned) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.pinned = pinned;
    }

    public void setInstant(Instant instant) {
        pinned.set(Objects.requireNonNull(instant, "instant"));
    }

    public void advance(Duration duration) {
        pinned.updateAndGet(current -> (current == null ? Clock.systemUTC().instant() : current).plus(duration));
    }

    /** Back to following the system clock. */
    public void reset() {
        pinned.set(null);
    }

    @Override
    public Instant instant() {
        Instant current = pinned.get();
        return current != null ? current : Clock.systemUTC().instant();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(newZone, pinned);
    }
}
