package io.github.eariver.wayfarer.frontier.cmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** R-05: only an admin may move another player to the gate. */
class FrontierGateTest {

    @Test
    @DisplayName("a non-admin cannot teleport another player")
    void nonAdminCannotTeleportOther() {
        assertFalse(FrontierGate.mayTeleportOther(false));
    }

    @Test
    @DisplayName("an admin can teleport another player")
    void adminCanTeleportOther() {
        assertTrue(FrontierGate.mayTeleportOther(true));
    }

    @Test
    @DisplayName("self-travel stays available to everyone")
    void selfTravelAlwaysAllowed() {
        assertTrue(FrontierGate.mayTravelSelf());
    }
}
