package dev.reconrail.access;

import java.time.*;
import java.util.function.Supplier;

/** A detected wall-clock reversal closes authentication until process/key reset. */
public final class SecurityClock extends Clock {
    private final Supplier<Instant> source;private Instant highWater;private boolean uncertain;
    public SecurityClock(){this(Instant::now);}
    SecurityClock(Supplier<Instant> source){this.source=source;}
    @Override public synchronized Instant instant(){Instant now=source.get();if(uncertain||(highWater!=null&&now.isBefore(highWater))){uncertain=true;throw new IllegalStateException("AUTH_CLOCK_UNCERTAIN");}highWater=now;return now;}
    @Override public ZoneId getZone(){return ZoneOffset.UTC;}
    @Override public Clock withZone(ZoneId zone){if(!zone.normalized().equals(ZoneOffset.UTC))throw new IllegalArgumentException("UTC_REQUIRED");return this;}
}
