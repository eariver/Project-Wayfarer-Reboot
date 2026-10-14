package io.github.eariver.wayfarer.frontier.launchpad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** R-13: repeated launchpad uses must never mint a second elytra. */
class ElytraDecisionTest {

    @Test
    @DisplayName("an equipped bound elytra is reused")
    void equippedIsReused() {
        assertEquals(LaunchpadManager.ElytraDecision.Action.ALREADY_EQUIPPED,
                LaunchpadManager.ElytraDecision.decide(true, false));
        assertEquals(LaunchpadManager.ElytraDecision.Action.ALREADY_EQUIPPED,
                LaunchpadManager.ElytraDecision.decide(true, true));
    }

    @Test
    @DisplayName("a bound elytra in the inventory is equipped")
    void inventoryOneIsEquipped() {
        assertEquals(LaunchpadManager.ElytraDecision.Action.EQUIP_FROM_INVENTORY,
                LaunchpadManager.ElytraDecision.decide(false, true));
    }

    @Test
    @DisplayName("only a player with no bound elytra at all is minted one")
    void mintsOnlyWhenMissing() {
        assertEquals(LaunchpadManager.ElytraDecision.Action.MINT,
                LaunchpadManager.ElytraDecision.decide(false, false));
    }

    @Test
    @DisplayName("a second launch reuses the same elytra instead of minting again")
    void repeatedLaunchesAreIdempotent() {
        LaunchpadManager.ElytraDecision.Action first =
                LaunchpadManager.ElytraDecision.decide(false, false);
        LaunchpadManager.ElytraDecision.Action second =
                LaunchpadManager.ElytraDecision.decide(true, false);
        assertEquals(LaunchpadManager.ElytraDecision.Action.MINT, first);
        assertEquals(LaunchpadManager.ElytraDecision.Action.ALREADY_EQUIPPED, second);
    }
}
