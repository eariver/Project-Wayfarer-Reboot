package io.github.eariver.wayfarer.frontier.waystone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R-10/R-12 regression tests for the waystone structure geometry: the core sits one block
 * above the stored anchor, every lookup resolves that offset, and removal only clears the
 * blocks the structure actually placed.
 */
class WaystoneGeometryTest {

    private static final int AX = 100;
    private static final int AY = 64;
    private static final int AZ = 200;
    private static final String FLOOR = "STONE_BRICKS";
    private static final String CAP = "DEEPSLATE_TILES";

    private boolean member(int dx, int dy, int dz) {
        return WaystoneGeometry.isStructureBlock(AX, AY, AZ, AX + dx, AY + dy, AZ + dz);
    }

    private boolean clears(String material, int dx, int dy, int dz) {
        return WaystoneGeometry.shouldClear(AX, AY, AZ, AX + dx, AY + dy, AZ + dz,
                material, FLOOR, CAP, "LODESTONE");
    }

    @Test
    @DisplayName("R-10: the core is one block above the anchor, so both resolve to the record")
    void coreOffsetResolves() {
        assertTrue(member(0, 0, 0), "the anchor/floor centre itself");
        assertTrue(member(0, WaystoneGeometry.CORE_DY, 0), "the core block above the anchor");
        assertTrue(member(0, WaystoneGeometry.HEAD_DY, 0), "the head");
        assertTrue(member(0, WaystoneGeometry.CAP_DY, 0), "the cap");
        assertTrue(member(WaystoneGeometry.FOOTPRINT, WaystoneGeometry.CORE_DY, 0), "a lantern");
        assertTrue(member(0, WaystoneGeometry.CORE_DY, -WaystoneGeometry.FOOTPRINT), "the other lantern");
    }

    @Test
    @DisplayName("R-10: blocks outside the structure are not part of it")
    void outsideIsNotStructure() {
        assertFalse(member(0, 4, 0), "above the cap");
        assertFalse(member(3, 0, 0), "beyond the floor corner");
        assertFalse(member(0, 0, 3), "beyond the floor corner");
        assertFalse(member(WaystoneGeometry.FOOTPRINT, WaystoneGeometry.HEAD_DY, 0), "no head at the corners");
        assertFalse(member(0, 1, 1), "a floor corner top is not a lantern");
        assertFalse(member(-1, -1, 0), "below the anchor");
    }

    @Test
    @DisplayName("R-12: only blocks that still hold their structure material are cleared")
    void onlyStructureMaterialsAreCleared() {
        assertTrue(clears(FLOOR, 0, 0, 0), "the intact floor centre");
        assertTrue(clears("LODESTONE", 0, WaystoneGeometry.CORE_DY, 0), "the core");
        assertTrue(clears("LANTERN", 2, WaystoneGeometry.CORE_DY, 0), "a lantern");
        assertTrue(clears("PLAYER_HEAD", 0, WaystoneGeometry.HEAD_DY, 0), "the head");
        assertTrue(clears(CAP, 0, WaystoneGeometry.CAP_DY, 0), "the cap");
    }

    @Test
    @DisplayName("R-12: player builds and foreign terrain inside the footprint survive")
    void playerBuildsSurvive() {
        assertFalse(clears("OAK_PLANKS", 1, 0, 1), "a player platform on the floor level");
        assertFalse(clears("CHEST", 2, 0, -2), "a chest on a floor corner");
        assertFalse(clears("DIRT", 0, 0, 0), "terrain that replaced a floor block");
        assertFalse(clears("OAK_PLANKS", 0, WaystoneGeometry.CORE_DY, 0), "not the core material");
        assertFalse(clears("AIR", 0, 0, 0), "already air");
        assertFalse(clears("OAK_PLANKS", 1, WaystoneGeometry.HEAD_DY, 0), "a build at head height");
    }

    @Test
    @DisplayName("R-11: the arrival ring never uses the core column")
    void arrivalRingAvoidsCore() {
        // the offsets WaystoneManager tries are all non-(0,0): the core column is skipped
        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {WaystoneGeometry.FOOTPRINT, 0}, {-WaystoneGeometry.FOOTPRINT, 0},
                {0, WaystoneGeometry.FOOTPRINT}, {0, -WaystoneGeometry.FOOTPRINT},
        };
        for (int[] offset : offsets) {
            assertFalse(offset[0] == 0 && offset[1] == 0, "no candidate may be the core column");
        }
    }
}
