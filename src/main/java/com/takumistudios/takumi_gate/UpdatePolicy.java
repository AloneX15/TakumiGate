package com.takumistudios.takumi_gate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * When a player running an old version of the event must restart, and what to tell them meanwhile. Pure logic: no
 * Minecraft classes, so it is covered by unit tests.
 */
public final class UpdatePolicy {
    /** Minutes before the deadline at which a reminder is sent. */
    static final int[] WARN_MINUTES = {10, 5, 1};

    private UpdatePolicy() {
    }

    /** A published version of the instance, as returned by {@code GET /api/gate/status}. */
    public record Release(int version, boolean restartRequired, int graceMinutes, Instant publishedAt) {
    }

    /**
     * Moment a player on {@code playerVersion} must be gone, or empty when no newer release needs a restart. With
     * several such releases the earliest deadline wins (a critical fix published with grace 0 is immediate).
     */
    public static Optional<Instant> deadline(int playerVersion, List<Release> releases) {
        return releases.stream()
                .filter(r -> r.version() > playerVersion && r.restartRequired())
                .map(r -> r.publishedAt().plus(Duration.ofMinutes(Math.max(0, r.graceMinutes()))))
                .min(Instant::compareTo);
    }

    /** Newest version that needs a restart for this player, or 0. */
    public static int targetVersion(int playerVersion, List<Release> releases) {
        return releases.stream()
                .filter(r -> r.version() > playerVersion && r.restartRequired())
                .mapToInt(Release::version)
                .max()
                .orElse(0);
    }

    public enum Action {
        NONE,
        /** First notice: title and chat message with the time left. */
        ANNOUNCE,
        /** Reminder with the minutes left. */
        WARN,
        /** Grace period over: disconnect the player. */
        KICK
    }

    /** What has already been said to one player about the current deadline. */
    public static final class Notified {
        boolean announced;
        int lastWarned = Integer.MAX_VALUE;
        Instant deadline;

        /** A new (different) deadline starts the notices again. */
        void track(Instant newDeadline) {
            if (!newDeadline.equals(deadline)) {
                deadline = newDeadline;
                announced = false;
                lastWarned = Integer.MAX_VALUE;
            }
        }
    }

    /** Next step for one player; updates {@code notified} so each notice is sent once. */
    public static Action decide(Instant now, Instant deadline, Notified notified) {
        notified.track(deadline);
        if (!now.isBefore(deadline)) {
            return Action.KICK;
        }
        if (!notified.announced) {
            notified.announced = true;
            notified.lastWarned = minutesLeft(now, deadline);
            return Action.ANNOUNCE;
        }
        int left = minutesLeft(now, deadline);
        for (int mark : WARN_MINUTES) {
            if (left <= mark && notified.lastWarned > mark) {
                notified.lastWarned = mark;
                return Action.WARN;
            }
        }
        return Action.NONE;
    }

    /** Whole minutes left, rounded up (59 s left is "1 min"). */
    public static int minutesLeft(Instant now, Instant deadline) {
        long seconds = Math.max(0, Duration.between(now, deadline).toSeconds());
        return (int) Math.min(Integer.MAX_VALUE, (seconds + 59) / 60);
    }
}
