package de.tum.cit.ase.util;

import java.time.Duration;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;

public enum CustomTimeUnit implements TemporalUnit {
    TEN_SECONDS("10 seconds", Duration.ofSeconds(10));

    private final String name;
    private final Duration duration;

    CustomTimeUnit(String name, Duration estimatedDuration) {
        this.name = name;
        this.duration = estimatedDuration;
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

    @Override
    public boolean isDurationEstimated() {
        return true;
    }

    @Override
    public boolean isDateBased() {
        return false;
    }

    @Override
    public boolean isTimeBased() {
        return false;
    }

    @Override
    public boolean isSupportedBy(Temporal temporal) {
        return TemporalUnit.super.isSupportedBy(temporal);
    }

    @Override
    public <R extends Temporal> R addTo(R temporal, long amount) {
        return (R) temporal.plus(amount, this);
    }

    @Override
    public long between(Temporal temporal1Inclusive, Temporal temporal2Exclusive) {
        return temporal1Inclusive.until(temporal2Exclusive, this);
    }
}
