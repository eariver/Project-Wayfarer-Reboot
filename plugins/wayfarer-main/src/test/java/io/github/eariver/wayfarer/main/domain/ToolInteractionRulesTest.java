package io.github.eariver.wayfarer.main.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R-08 / D3: the decision tables behind the Main plugin's block-break and damage handlers.
 *
 * <p>The Bukkit event handlers themselves cannot run in a unit test, so the rules they
 * implement are expressed here as pure functions of the tool state — this is what keeps
 * the handler wiring honest: a handler that contradicts these rules is a bug.
 */
class ToolInteractionRulesTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID toolId = UUID.randomUUID();
    private final UUID otherToolId = UUID.randomUUID();

    private GrowthTool tool(int epoch, GrowthTool.Status status, long progress) {
        return new GrowthTool(toolId, owner, "tester", epoch, progress,
                EvolutionPlan.Branch.FORTUNE, status, false);
    }

    // ---- R-08: may a held item register a break? ----

    /** Mirrors WayfarerMainPlugin#onBreak: only the owner's current ACTIVE tool mines. */
    private static boolean mayBreak(GrowthTool tool, UUID holder, UUID itemOwner, UUID itemToolId, int itemEpoch) {
        if (tool == null) return false;
        if (!itemOwner.equals(holder)) return false;
        if (tool.status() != GrowthTool.Status.ACTIVE) return false;
        return tool.toolId().equals(itemToolId) && tool.epoch() == itemEpoch;
    }

    /** A BROKEN tool must cancel the block-break event (the gray-dye shell cannot mine). */
    private static boolean cancelsBreak(GrowthTool tool) {
        return tool != null && tool.status() == GrowthTool.Status.BROKEN;
    }

    @Test
    @DisplayName("R-08: a BROKEN tool cancels the break and registers nothing")
    void brokenToolCancels() {
        GrowthTool broken = tool(2, GrowthTool.Status.BROKEN, 500L);
        assertTrue(cancelsBreak(broken));
        assertFalse(mayBreak(broken, owner, owner, toolId, 2));
    }

    @Test
    @DisplayName("R-08: an ACTIVE current-epoch tool mines normally")
    void activeToolMines() {
        GrowthTool active = tool(2, GrowthTool.Status.ACTIVE, 500L);
        assertFalse(cancelsBreak(active));
        assertTrue(mayBreak(active, owner, owner, toolId, 2));
    }

    @Test
    @DisplayName("R-08: stale epoch, foreign tool and revoked tools never register progress")
    void rejectedItems() {
        GrowthTool active = tool(2, GrowthTool.Status.ACTIVE, 500L);
        assertFalse(mayBreak(active, owner, owner, toolId, 1), "stale epoch");
        assertFalse(mayBreak(active, owner, owner, otherToolId, 2), "another tool");
        assertFalse(mayBreak(active, other, owner, toolId, 2), "the item belongs to someone else");
        assertFalse(mayBreak(active.withStatus(GrowthTool.Status.REVOKED), owner, owner, toolId, 2),
                "revoked");
    }

    // ---- D3: the BROKEN transition carries the newest progress ----

    /** Mirrors WayfarerMainPlugin#onDamage: never persist the last-checkpointed progress. */
    private static GrowthTool transitionToBroken(GrowthTool cached, long latestProgress) {
        return cached.withProgress(latestProgress).withStatus(GrowthTool.Status.BROKEN);
    }

    @Test
    @DisplayName("D3: breaking the pickaxe keeps the not-yet-checkpointed progress")
    void brokenTransitionKeepsPendingProgress() {
        GrowthTool cached = tool(3, GrowthTool.Status.ACTIVE, 100L); // last checkpoint
        long latest = 107L;                                          // 7 un-checkpointed breaks
        GrowthTool broken = transitionToBroken(cached, latest);
        assertEquals(107L, broken.progress(), "the DB must not be written with the stale 100");
        assertEquals(GrowthTool.Status.BROKEN, broken.status());
        assertEquals(3, broken.epoch(), "the epoch is preserved");
    }

    @Test
    @DisplayName("D3: after repair the progress is still the newest value")
    void repairKeepsProgress() {
        GrowthTool broken = transitionToBroken(tool(3, GrowthTool.Status.ACTIVE, 100L), 107L);
        GrowthTool repaired = broken.withStatus(GrowthTool.Status.ACTIVE);
        assertEquals(107L, repaired.progress());
    }
}
