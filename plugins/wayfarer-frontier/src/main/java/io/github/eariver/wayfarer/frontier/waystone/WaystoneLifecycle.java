package io.github.eariver.wayfarer.frontier.waystone;

/**
 * Pure waystone lifecycle rules (no Bukkit/DB): state transitions and deadline math.
 *
 * <p>PROTECTED (protectedDays) &rarr; CONTESTABLE (contestableDays) &rarr; DORMANT
 * (dormantDays) &rarr; RUINED. Records store initial deadlines; state is derived from
 * the current time so a restart can never roll a lifecycle back.
 */
public final class WaystoneLifecycle {

    public enum State { PROTECTED, CONTESTABLE, DORMANT, RUINED }

    private WaystoneLifecycle() {
    }

    public record Deadlines(long protectedUntil, long contestableUntil, long dormantUntil) {
    }

    public static Deadlines deadlinesFor(long now, long protectedDays, long contestableDays, long dormantDays) {
        long p = now + protectedDays * 86400L;
        long c = now + (protectedDays + contestableDays) * 86400L;
        long d = now + (protectedDays + contestableDays + dormantDays) * 86400L;
        return new Deadlines(p, c, d);
    }

    public static State stateAt(Deadlines deadlines, long now) {
        if (now <= deadlines.protectedUntil()) return State.PROTECTED;
        if (now <= deadlines.contestableUntil()) return State.CONTESTABLE;
        if (now <= deadlines.dormantUntil()) return State.DORMANT;
        return State.RUINED;
    }

    /** Which lifecycle ops are allowed for a state (maintain/overwrite/revive). */
    public static boolean operationAllowed(State state, String operation) {
        return switch (operation) {
            case "maintain" -> state == State.PROTECTED || state == State.CONTESTABLE;
            case "overwrite" -> state == State.CONTESTABLE;
            case "revive" -> state == State.DORMANT;
            default -> false;
        };
    }

    /** Teleport destinations must be discovered and in a teleportable state. */
    public static boolean teleportable(State state) {
        return state == State.PROTECTED || state == State.CONTESTABLE;
    }
}
