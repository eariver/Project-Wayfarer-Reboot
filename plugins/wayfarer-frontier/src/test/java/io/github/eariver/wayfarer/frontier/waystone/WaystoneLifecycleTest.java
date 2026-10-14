package io.github.eariver.wayfarer.frontier.waystone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaystoneLifecycleTest {

    private static final long DAY = 86400L;
    private static final long NOW = 1_800_000_000L;

    private final WaystoneLifecycle.Deadlines deadlines =
            WaystoneLifecycle.deadlinesFor(NOW, 14, 14, 180);

    @Test
    void deadlinesFollowProtectedContestableDormantChain() {
        assertEquals(NOW + 14 * DAY, deadlines.protectedUntil());
        assertEquals(NOW + 28 * DAY, deadlines.contestableUntil());
        assertEquals(NOW + 208 * DAY, deadlines.dormantUntil());
    }

    @Test
    void stateTransitionsAcrossTime() {
        assertEquals(WaystoneLifecycle.State.PROTECTED, WaystoneLifecycle.stateAt(deadlines, NOW));
        assertEquals(WaystoneLifecycle.State.PROTECTED, WaystoneLifecycle.stateAt(deadlines, NOW + 14 * DAY));
        assertEquals(WaystoneLifecycle.State.CONTESTABLE, WaystoneLifecycle.stateAt(deadlines, NOW + 14 * DAY + 1));
        assertEquals(WaystoneLifecycle.State.CONTESTABLE, WaystoneLifecycle.stateAt(deadlines, NOW + 28 * DAY));
        assertEquals(WaystoneLifecycle.State.DORMANT, WaystoneLifecycle.stateAt(deadlines, NOW + 28 * DAY + 1));
        assertEquals(WaystoneLifecycle.State.DORMANT, WaystoneLifecycle.stateAt(deadlines, NOW + 208 * DAY));
        assertEquals(WaystoneLifecycle.State.RUINED, WaystoneLifecycle.stateAt(deadlines, NOW + 208 * DAY + 1));
    }

    @Test
    void operationPermissionsPerState() {
        assertTrue(WaystoneLifecycle.operationAllowed(WaystoneLifecycle.State.PROTECTED, "maintain"));
        assertTrue(WaystoneLifecycle.operationAllowed(WaystoneLifecycle.State.CONTESTABLE, "overwrite"));
        assertFalse(WaystoneLifecycle.operationAllowed(WaystoneLifecycle.State.PROTECTED, "overwrite"));
        assertFalse(WaystoneLifecycle.operationAllowed(WaystoneLifecycle.State.CONTESTABLE, "revive"));
        assertTrue(WaystoneLifecycle.operationAllowed(WaystoneLifecycle.State.DORMANT, "revive"));
        assertFalse(WaystoneLifecycle.operationAllowed(WaystoneLifecycle.State.RUINED, "maintain"));
        assertFalse(WaystoneLifecycle.operationAllowed(WaystoneLifecycle.State.PROTECTED, "bogus"));
    }

    @Test
    void onlyProtectedAndContestableAreTeleportable() {
        assertTrue(WaystoneLifecycle.teleportable(WaystoneLifecycle.State.PROTECTED));
        assertTrue(WaystoneLifecycle.teleportable(WaystoneLifecycle.State.CONTESTABLE));
        assertFalse(WaystoneLifecycle.teleportable(WaystoneLifecycle.State.DORMANT));
        assertFalse(WaystoneLifecycle.teleportable(WaystoneLifecycle.State.RUINED));
    }
}
