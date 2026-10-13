package io.github.eariver.wayfarer.main;

import io.github.eariver.wayfarer.main.domain.EvolutionPlan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R-07: the item's material/name mapping is the source of truth the GUI, the status
 * command and the physical item must all follow.
 */
class GrowthItemsTest {

    @Test
    @DisplayName("a Diamond-tier tool without the upgrade stays a Diamond pickaxe")
    void diamondWithoutUpgrade() {
        assertEquals(org.bukkit.Material.DIAMOND_PICKAXE,
                GrowthItems.materialFor(EvolutionPlan.DIAMOND, false));
        assertEquals("Diamond", GrowthItems.tierName(EvolutionPlan.DIAMOND, false));
    }

    @Test
    @DisplayName("only an upgraded tool reports Netherite")
    void netheriteOnlyWhenUpgraded() {
        assertEquals(org.bukkit.Material.NETHERITE_PICKAXE,
                GrowthItems.materialFor(EvolutionPlan.DIAMOND, true));
        assertEquals("Netherite", GrowthItems.tierName(EvolutionPlan.DIAMOND, true));
    }

    @Test
    @DisplayName("tiers above Diamond keep the Diamond base until the upgrade")
    void postDiamondTiersStayDiamond() {
        for (int tier = EvolutionPlan.DIAMOND + 1; tier <= EvolutionPlan.DIAMOND + 3; tier++) {
            assertEquals(org.bukkit.Material.DIAMOND_PICKAXE, GrowthItems.materialFor(tier, false),
                    "tier " + tier + " without the upgrade must remain Diamond");
            assertTrue(GrowthItems.tierName(tier, false).startsWith("Diamond"));
        }
    }

    @Test
    @DisplayName("the lower tiers follow the concept baseline")
    void lowerTiers() {
        assertEquals(org.bukkit.Material.STONE_PICKAXE, GrowthItems.materialFor(1, false));
        assertEquals(org.bukkit.Material.IRON_PICKAXE, GrowthItems.materialFor(2, false));
        assertEquals("Stone", GrowthItems.tierName(1, false));
        assertEquals("Iron", GrowthItems.tierName(2, false));
    }
}
