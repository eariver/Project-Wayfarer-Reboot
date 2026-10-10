package io.github.eariver.wayfarer.main.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepairPricingTest {

    private final RepairPricing pricing = RepairPricing.defaults();

    @Test
    void fullRepairFollowsEvolutionMultiplier() {
        assertEquals(100, pricing.fullRepairCost(0));
        assertEquals(108, pricing.fullRepairCost(1));
        assertEquals(116, pricing.fullRepairCost(2));
    }

    @Test
    void partialRepairRespectsMinimumChargeRatio() {
        // damage 0 -> minimum 25% charge
        assertEquals(25, pricing.partialRepairCost(0, 0, 100));
        // damage half
        assertEquals(50, pricing.partialRepairCost(0, 50, 100));
        // full damage
        assertEquals(100, pricing.partialRepairCost(0, 100, 100));
    }

    @Test
    void brokenRepairAddsSurcharges() {
        assertEquals(200, pricing.brokenRepairCost(0));
        assertEquals(213, pricing.brokenRepairCost(1)); // 108 + 100 + 5
    }

    @Test
    void reissueIsStrictlyAboveAnyRepair() {
        assertTrue(pricing.reissueCost(0, false) > pricing.fullRepairCost(0));
        assertTrue(pricing.reissueCost(1, true) > pricing.brokenRepairCost(1));
    }
}
