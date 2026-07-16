package com.jereplatform.platform;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

final class AdjustableClock {

    private final AtomicReference<Instant> current =
        new AtomicReference<>(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));

    Clock asClock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                if (!ZoneOffset.UTC.equals(zone)) {
                    throw new IllegalArgumentException("Only UTC is supported");
                }
                return this;
            }

            @Override
            public Instant instant() {
                return current.get();
            }
        };
    }

    void reset() {
        current.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    void advance(Duration duration) {
        current.updateAndGet(value -> value.plus(duration));
    }
}
