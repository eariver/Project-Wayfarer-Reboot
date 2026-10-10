package io.github.eariver.wayfarer.main.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvolutionPlanTest {

    private final EvolutionPlan plan = EvolutionPlan.defaults();

    @Test
    void thresholdsAreCumulativeAndMatchSpecDefaults() {
        assertEquals(100, plan.thresholdFor(1));
        assertEquals(400, plan.thresholdFor(2));
        assertEquals(1200, plan.thresholdFor(3));
        // netherite base at 1200; post-diamond increments 800/800+200/...
        assertEquals(2000, plan.thresholdFor(4));
        assertEquals(3040, plan.thresholdFor(5));
        assertEquals(4400, plan.thresholdFor(6));
    }

    @Test
    void tierFollowsCumulativeProgress() {
        assertEquals(0, plan.tierFor(0));
        assertEquals(0, plan.tierFor(99));
        assertEquals(1, plan.tierFor(100));
        assertEquals(1, plan.tierFor(399));
        assertEquals(2, plan.tierFor(400));
        assertEquals(3, plan.tierFor(1200));
        assertEquals(3, plan.tierFor(1999));
        assertEquals(4, plan.tierFor(2000));
    }

    @Test
    void evolutionProgressFractionIsBounded() {
        assertTrue(plan.fractionToNext(0, 0) == 0.0);
        assertTrue(plan.fractionToNext(0, 50) == 0.5);
        assertTrue(plan.fractionToNext(0, 500) == 1.0);
    }

    @Test
    void enchantmentCycleAndCaps() {
        assertEquals(EnchantKind.EFFICIENCY, plan.enchantmentFor(1, EvolutionPlan.Branch.FORTUNE).kind());
        assertEquals(1, plan.enchantmentFor(1, EvolutionPlan.Branch.FORTUNE).level());
        assertEquals(EnchantKind.UNBREAKING, plan.enchantmentFor(2, EvolutionPlan.Branch.FORTUNE).kind());
        assertEquals(EnchantKind.UNBREAKING, plan.enchantmentFor(4, EvolutionPlan.Branch.FORTUNE).kind());
        assertEquals(2, plan.enchantmentFor(4, EvolutionPlan.Branch.FORTUNE).level());
        assertEquals(EnchantKind.FORTUNE, plan.enchantmentFor(5, EvolutionPlan.Branch.FORTUNE).kind());
        // cycle continues: 6=EFFICIENCY, 7=UNBREAKING, ...; evolution 13 is the 6th EFFICIENCY application
        assertEquals(EnchantKind.EFFICIENCY, plan.enchantmentFor(13, EvolutionPlan.Branch.FORTUNE).kind());
        assertEquals(6, plan.enchantmentFor(13, EvolutionPlan.Branch.FORTUNE).level());
        // cap enforcement at 10
        assertEquals(10, plan.enchantmentFor(46, EvolutionPlan.Branch.FORTUNE).level());
    }

    @Test
    void silkBranchReplacesFortuneWithSilkTouch() {
        assertEquals(EnchantKind.SILK_TOUCH, plan.enchantmentFor(5, EvolutionPlan.Branch.SILK_TOUCH).kind());
        assertEquals(1, plan.enchantmentFor(5, EvolutionPlan.Branch.SILK_TOUCH).level());
        assertEquals(EnchantKind.EFFICIENCY, plan.enchantmentFor(6, EvolutionPlan.Branch.SILK_TOUCH).kind());
    }
}
