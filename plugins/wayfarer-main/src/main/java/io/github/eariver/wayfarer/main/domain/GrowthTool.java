package io.github.eariver.wayfarer.main.domain;

import java.util.UUID;

/**
 * A player's Growth Pickaxe logical tool. One stable row per logical tool; a reissue
 * bumps {@code epoch} in place (P-10) so the tool never disappears from the database.
 */
public record GrowthTool(
        UUID toolId,
        UUID ownerUuid,
        String ownerName,
        int epoch,
        long progress,
        EvolutionPlan.Branch branch,
        Status status,
        boolean netheriteUpgraded
) {
    public enum Status { ACTIVE, BROKEN, REVOKED }

    /** Minimal physical identity read from the item's PDC. */
    public record Identity(String toolId, int epoch, String ownerUuid) {
    }

    /** Legacy rows without the netherite flag read as not upgraded. */
    public GrowthTool {
    }

    public GrowthTool withProgress(long progress) {
        return new GrowthTool(toolId, ownerUuid, ownerName, epoch, progress, branch, status, netheriteUpgraded);
    }

    public GrowthTool withStatus(Status status) {
        return new GrowthTool(toolId, ownerUuid, ownerName, epoch, progress, branch, status, netheriteUpgraded);
    }

    public GrowthTool withEpoch(int epoch) {
        return new GrowthTool(toolId, ownerUuid, ownerName, epoch, progress, branch, status, netheriteUpgraded);
    }

    public GrowthTool withBranch(EvolutionPlan.Branch branch) {
        return new GrowthTool(toolId, ownerUuid, ownerName, epoch, progress, branch, status, netheriteUpgraded);
    }

    public GrowthTool withNetheriteUpgraded(boolean value) {
        return new GrowthTool(toolId, ownerUuid, ownerName, epoch, progress, branch, status, value);
    }

    public GrowthTool withOwnerName(String ownerName) {
        return new GrowthTool(toolId, ownerUuid, ownerName, epoch, progress, branch, status, netheriteUpgraded);
    }
}
