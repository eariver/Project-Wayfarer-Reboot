package io.github.eariver.wayfarer.main.domain;

/**
 * Growth progression plan (concept v0.0.5 aligned, P-19).
 *
 * <p>Material evolution: Stone(100) &rarr; Iron(400) &rarr; Diamond(1200) — cumulative
 * breaks. Enchantment evolution starts only AFTER Diamond is reached: enchant step
 * {@code n} (1-based) needs {@code base + linear*n + quadratic*n^2} additional breaks,
 * so tier 4 lands at 1200 + 1040 = 2240.
 *
 * <p>Netherite is NOT an automatic evolution. It is a persistent, WM-paid upgrade applied
 * on top of a Diamond base tool (see {@link #effectiveMaterial}).
 */
public final class EvolutionPlan {

    public enum Branch { FORTUNE, SILK_TOUCH }


    public static final int STONE = 1;
    public static final int IRON = 2;
    public static final int DIAMOND = 3;
    public static final int FIRST_ENCHANT_TIER = 4;

    private static final int CAP_EFFICIENCY = 10;
    private static final int CAP_UNBREAKING = 10;
    private static final int CAP_FORTUNE = 5;
    private static final int CAP_SILK = 1;

    private final int stoneThreshold;
    private final int ironThreshold;
    private final int diamondThreshold;
    private final int enchantBase;
    private final int enchantLinear;
    private final int enchantQuadratic;

    public EvolutionPlan(int stoneThreshold, int ironThreshold, int diamondThreshold,
                         int enchantBase, int enchantLinear, int enchantQuadratic) {
        this.stoneThreshold = stoneThreshold;
        this.ironThreshold = ironThreshold;
        this.diamondThreshold = diamondThreshold;
        this.enchantBase = enchantBase;
        this.enchantLinear = enchantLinear;
        this.enchantQuadratic = enchantQuadratic;
    }

    public static EvolutionPlan defaults() {
        return new EvolutionPlan(100, 400, 1200, 800, 200, 40);
    }

    /**
     * Cumulative breaks required to reach the given tier. Tier 1 is the starting material
     * (Stone) and needs no progress; the material thresholds (100/400/1200) match the
     * concept baseline and existing player data, so the PoC's live progress is preserved.
     */
    public long thresholdFor(int tier) {
        return switch (tier) {
            case 0, 1 -> 0;                       // starting material
            case 2 -> stoneThreshold;             // Stone -> Iron at 100
            case 3 -> ironThreshold;              // Iron -> Diamond at 400
            case 4 -> diamondThreshold;           // Diamond (and first enchant step) at 1200
            default -> {
                long total = diamondThreshold;
                for (int n = 1; n <= tier - FIRST_ENCHANT_TIER; n++) {
                    total += enchantIncrement(n);
                }
                yield total;
            }
        };
    }

    /** Breaks needed for the n-th enchant evolution (n = 1 for the first one after Diamond). */
    public long enchantIncrement(int n) {
        return enchantBase + enchantLinear * (long) n + enchantQuadratic * (long) n * n;
    }

    public int tierFor(long cumulative) {
        int tier = STONE;
        while (cumulative >= thresholdFor(tier + 1) && tier < 4096) {
            tier++;
        }
        return tier;
    }

    public boolean shouldEvolve(int currentTier, long cumulative) {
        return cumulative >= thresholdFor(currentTier + 1);
    }

    public double fractionToNext(int tier, long cumulative) {
        long lower = tier <= STONE ? 0 : thresholdFor(tier);
        long upper = thresholdFor(tier + 1);
        if (upper <= lower) return 1.0;
        return Math.clamp((double) (cumulative - lower) / (double) (upper - lower), 0.0, 1.0);
    }

    /** 1-based enchant evolution count (0 while still below Diamond). */
    public int enchantSteps(int tier) {
        return Math.max(0, tier - DIAMOND);
    }

    /** Which enchantment the n-th enchant step (1-based) grants, and at what level. */
    public record EnchantmentStep(EnchantKind kind, int level) {
    }

    public EnchantmentStep enchantmentFor(int enchantStep, Branch branch) {
        // cycle: Efficiency, Unbreaking, Efficiency, Unbreaking, Fortune
        EnchantKind base = switch ((enchantStep - 1) % 5) {
            case 0, 2 -> EnchantKind.EFFICIENCY;
            case 1, 3 -> EnchantKind.UNBREAKING;
            default -> EnchantKind.FORTUNE;
        };
        if (base == EnchantKind.FORTUNE && branch == Branch.SILK_TOUCH) {
            return new EnchantmentStep(EnchantKind.SILK_TOUCH, 1);
        }
        int applications = applicationsFor(base, enchantStep);
        return new EnchantmentStep(base, Math.min(applications, capFor(base)));
    }

    private int applicationsFor(EnchantKind kind, int enchantStep) {
        int count = 0;
        for (int n = 1; n <= enchantStep; n++) {
            EnchantKind at = switch ((n - 1) % 5) {
                case 0, 2 -> EnchantKind.EFFICIENCY;
                case 1, 3 -> EnchantKind.UNBREAKING;
                default -> EnchantKind.FORTUNE;
            };
            if (at == kind) count++;
        }
        return count;
    }

    public static int capFor(EnchantKind kind) {
        return switch (kind) {
            case EFFICIENCY -> CAP_EFFICIENCY;
            case UNBREAKING -> CAP_UNBREAKING;
            case FORTUNE -> CAP_FORTUNE;
            case SILK_TOUCH -> CAP_SILK;
        };
    }

    /** Netherite is a persistent upgrade on top of Diamond, never derived from progress alone. */
    public static boolean netheriteEffective(int tier, boolean netheriteUpgraded) {
        return tier >= DIAMOND && netheriteUpgraded;
    }
}
