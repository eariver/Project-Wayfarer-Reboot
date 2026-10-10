package io.github.eariver.wayfarer.main.domain;

/** Repair and reissue pricing for the Growth Pickaxe (initial defaults from the concept specs). */
public final class RepairPricing {

    private final double fullBase;
    private final double evolutionMultiplier;
    private final double minimumChargeRatio;
    private final double brokenFlatSurcharge;
    private final double brokenSurchargePerEvolution;

    public RepairPricing(double fullBase, double evolutionMultiplier, double minimumChargeRatio,
                         double brokenFlatSurcharge, double brokenSurchargePerEvolution) {
        this.fullBase = fullBase;
        this.evolutionMultiplier = evolutionMultiplier;
        this.minimumChargeRatio = minimumChargeRatio;
        this.brokenFlatSurcharge = brokenFlatSurcharge;
        this.brokenSurchargePerEvolution = brokenSurchargePerEvolution;
    }

    public static RepairPricing defaults() {
        return new RepairPricing(100, 0.08, 0.25, 100, 5);
    }

    public long fullRepairCost(int evolution) {
        return (long) Math.ceil(fullBase * (1 + evolution * evolutionMultiplier));
    }

    public long partialRepairCost(int evolution, int damage, int maxDurability) {
        double ratio = maxDurability > 0 ? (double) damage / maxDurability : 0;
        double charge = Math.max(minimumChargeRatio, ratio);
        return (long) Math.ceil(fullRepairCost(evolution) * charge);
    }

    public long brokenRepairCost(int evolution) {
        return fullRepairCost(evolution) + (long) brokenFlatSurcharge + (long) (brokenSurchargePerEvolution * evolution);
    }

    /** Paid reissue must be strictly above the repair quote for the same state. */
    public long reissueCost(int evolution, boolean broken) {
        long repair = broken ? brokenRepairCost(evolution) : fullRepairCost(evolution);
        return repair + fullRepairCost(evolution);
    }
}
