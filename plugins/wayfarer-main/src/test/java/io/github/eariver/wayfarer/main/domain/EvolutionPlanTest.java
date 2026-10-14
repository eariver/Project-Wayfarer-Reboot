package io.github.eariver.wayfarer.main.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvolutionPlanTest {

    private final EvolutionPlan plan = EvolutionPlan.defaults();

    @Test
    void tier1IsTheStartingMaterialWithNoProgress() {
        assertEquals(0, plan.thresholdFor(1));
        assertEquals(1, plan.tierFor(0));
    }

    @Test
    void materialThresholdsMatchConceptBaseline() {
        assertEquals(100, plan.thresholdFor(2));   // Stone -> Iron
        assertEquals(400, plan.thresholdFor(3));   // Iron -> Diamond
        assertEquals(1200, plan.thresholdFor(4));  // Diamond
    }

    @Test
    void enchantEvolutionStartsOnlyAfterDiamond() {
        // first enchant step needs 800 + 200*1 + 40*1 = 1040 breaks after Diamond
        assertEquals(0, plan.enchantSteps(3));
        assertEquals(1, plan.enchantSteps(4));
        assertEquals(2240, plan.thresholdFor(5));  // 1200 + 1040
        assertEquals(3600, plan.thresholdFor(6));  // + 800 + 400 + 160
        assertEquals(5360, plan.thresholdFor(7));
    }

    @Test
    void tierFollowsCumulativeProgress() {
        assertEquals(1, plan.tierFor(99));
        assertEquals(2, plan.tierFor(100));
        assertEquals(2, plan.tierFor(399));
        assertEquals(3, plan.tierFor(400));
        assertEquals(3, plan.tierFor(1199));
        assertEquals(4, plan.tierFor(1200));   // Diamond + first enchant step
        assertEquals(4, plan.tierFor(2239));
        assertEquals(5, plan.tierFor(2240));
    }

    @Test
    void existingPlayerProgressIsPreserved() {
        // the Owner's recovered tool sits at progress 110 (Iron) under the old plan too
        assertEquals(2, plan.tierFor(110));
        assertEquals(3, plan.tierFor(400));
    }

    @Test
    void enchantCycleStartsAtEfficiencyAfterDiamond() {
        // tiers 1..3 are material evolution and must not grant enchants
        assertEquals(0, plan.enchantSteps(1));
        assertEquals(0, plan.enchantSteps(3));
        // enchant step 1..5 = Eff, Ub, Eff, Ub, Fortune
        assertEquals(EnchantKind.EFFICIENCY, plan.enchantmentFor(1, EvolutionPlan.Branch.FORTUNE).kind());
        assertEquals(1, plan.enchantmentFor(1, EvolutionPlan.Branch.FORTUNE).level());
        assertEquals(EnchantKind.UNBREAKING, plan.enchantmentFor(2, EvolutionPlan.Branch.FORTUNE).kind());
        assertEquals(EnchantKind.EFFICIENCY, plan.enchantmentFor(3, EvolutionPlan.Branch.FORTUNE).kind());
        assertEquals(2, plan.enchantmentFor(3, EvolutionPlan.Branch.FORTUNE).level());
        assertEquals(EnchantKind.FORTUNE, plan.enchantmentFor(5, EvolutionPlan.Branch.FORTUNE).kind());
        // step 6 repeats the cycle at efficiency level 3
        assertEquals(EnchantKind.EFFICIENCY, plan.enchantmentFor(6, EvolutionPlan.Branch.FORTUNE).kind());
        assertEquals(3, plan.enchantmentFor(6, EvolutionPlan.Branch.FORTUNE).level());
    }

    @Test
    void enchantCapsClampEffectiveLevels() {
        // efficiency is granted on steps 1,3,6,8,11,13,... -> 46th step is the 10th application
        assertEquals(10, plan.enchantmentFor(46, EvolutionPlan.Branch.FORTUNE).level());
        assertEquals(10, EvolutionPlan.capFor(EnchantKind.UNBREAKING));
        assertEquals(5, EvolutionPlan.capFor(EnchantKind.FORTUNE));
        assertEquals(1, EvolutionPlan.capFor(EnchantKind.SILK_TOUCH));
    }

    @Test
    void silkBranchReplacesFortuneOnly() {
        assertEquals(EnchantKind.SILK_TOUCH, plan.enchantmentFor(5, EvolutionPlan.Branch.SILK_TOUCH).kind());
        assertEquals(EnchantKind.EFFICIENCY, plan.enchantmentFor(6, EvolutionPlan.Branch.SILK_TOUCH).kind());
    }

    @Test
    void netheriteIsNeverDerivedFromProgressAlone() {
        assertFalse(EvolutionPlan.netheriteEffective(3, false));
        assertFalse(EvolutionPlan.netheriteEffective(4, false));
        assertTrue(EvolutionPlan.netheriteEffective(4, true));
        assertFalse(EvolutionPlan.netheriteEffective(2, true));
    }
}
