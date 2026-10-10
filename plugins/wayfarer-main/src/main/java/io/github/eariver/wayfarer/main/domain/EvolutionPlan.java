package io.github.eariver.wayfarer.main.domain;


/**
 * Growth progression plan. Progress is a cumulative count of qualifying block breaks
 * (uniform 1.00 per break — DEC-REQ-007 defaults, block weights were withdrawn).
 *
 * <p>Thresholds (cumulative breaks): stone&rarr;iron 100, iron&rarr;diamond 400,
 * diamond&rarr;netherite 1200, then each further evolution costs
 * {@code 800 + 200*n + 40*n^2} additional breaks (n = 0, 1, 2, ...).
 *
 * <p>Enchantment cycle per evolution: Efficiency, Unbreaking, Efficiency, Unbreaking,
 * Fortune (caps 10/10/5; Silk Touch replaces Fortune once for the SILK_TOUCH branch).
 */
public final class EvolutionPlan {

    public static final int STONE = 0;
    public static final int IRON = 1;
    public static final int DIAMOND = 2;
    public static final int NETHERITE_BASE = 3;

    private static final int CAP_EFFICIENCY = 10;
    private static final int CAP_UNBREAKING = 10;
    private static final int CAP_FORTUNE = 5;
    private static final int CAP_SILK = 1;

    public enum Branch { FORTUNE, SILK_TOUCH }

    private final int stoneThreshold;
    private final int ironThreshold;
    private final int diamondThreshold;
    private final int postBase;
    private final int postLinear;
    private final int postQuadratic;

    public EvolutionPlan(int stoneThreshold, int ironThreshold, int diamondThreshold,
                         int postBase, int postLinear, int postQuadratic) {
        this.stoneThreshold = stoneThreshold;
        this.ironThreshold = ironThreshold;
        this.diamondThreshold = diamondThreshold;
        this.postBase = postBase;
        this.postLinear = postLinear;
        this.postQuadratic = postQuadratic;
    }

    public static EvolutionPlan defaults() {
        return new EvolutionPlan(100, 400, 1200, 800, 200, 40);
    }

    /** Cumulative break count required to reach the given evolution tier. */
    public long thresholdFor(int evolution) {
        return switch (evolution) {
            case 0, 1, 2 -> switch (evolution) {
                case 1 -> stoneThreshold;
                case 2 -> ironThreshold;
                default -> diamondThreshold;
            };
            default -> {
                long n = evolution - NETHERITE_BASE; // netherite itself is the first post threshold
                yield diamondThreshold + sumPost(n);
            }
        };
    }

    private long sumPost(long n) {
        // sum_{i=0}^{n-1} (base + linear*i + quadratic*i^2)
        long sumBase = postBase * n;
        long sumLinear = postLinear * (n - 1) * n / 2;
        long sumQuad = postQuadratic * (n - 1) * n * (2L * n - 1) / 6;
        return sumBase + sumLinear + sumQuad;
    }

    /** Evolution tier for a cumulative progress count (saturating, non-recursive). */
    public int tierFor(long cumulative) {
        int tier = STONE;
        while (cumulative >= thresholdFor(tier + 1)) {
            tier++;
            if (tier > 64) break; // sanity bound; thresholdFor grows quadratically
        }
        return tier;
    }

    /** True when cumulative progress has reached the next evolution. */
    public boolean shouldEvolve(int currentTier, long cumulative) {
        return cumulative >= thresholdFor(currentTier + 1);
    }

    /** Progress toward the next evolution as a 0..1 fraction. */
    public double fractionToNext(int tier, long cumulative) {
        long lower = tier <= STONE ? 0 : thresholdFor(tier);
        long upper = thresholdFor(tier + 1);
        if (upper <= lower) return 1.0;
        return Math.clamp((double) (cumulative - lower) / (double) (upper - lower), 0.0, 1.0);
    }

    /** Which enchantment the n-th evolution (1-based) grants, and at what level. */
    public record EnchantmentStep(EnchantKind kind, int level) {
    }

    public EnchantmentStep enchantmentFor(int evolutionNumber, Branch branch) {
        // evolutionNumber is 1-based (first evolution = 1)
        EnchantKind base = switch ((evolutionNumber - 1) % 5) {
            case 0, 2 -> EnchantKind.EFFICIENCY;
            case 1, 3 -> EnchantKind.UNBREAKING;
            default -> EnchantKind.FORTUNE;
        };
        if (base == EnchantKind.FORTUNE && branch == Branch.SILK_TOUCH) {
            return new EnchantmentStep(EnchantKind.SILK_TOUCH, 1);
        }
        int applications = applicationsFor(base, evolutionNumber);
        int level = Math.min(applications, capFor(base));
        return new EnchantmentStep(base, level);
    }

    private int applicationsFor(EnchantKind kind, int evolutionNumber) {
        // cycle index: EFFICIENCY 0, UNBREAKING 1, EFFICIENCY 2, UNBREAKING 3, FORTUNE 4
        int count = 0;
        for (int n = 1; n <= evolutionNumber; n++) {
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
}
