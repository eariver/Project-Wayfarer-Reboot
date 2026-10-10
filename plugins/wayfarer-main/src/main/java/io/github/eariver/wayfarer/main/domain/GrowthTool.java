package io.github.eariver.wayfarer.main.domain;

import java.util.UUID;

/** A player's Growth Pickaxe logical tool record (progress survives item loss/death). */
public record GrowthTool(
        UUID toolId,
        UUID ownerUuid,
        String ownerName,
        int epoch,
        long progress,
        EvolutionPlan.Branch branch,
        Status status
) {
    public enum Status { ACTIVE, BROKEN, REVOKED }

    /** Minimal physical identity read from the item's PDC. */
    public record Identity(String toolId, int epoch, String ownerUuid) {
    }

    public GrowthTool withProgress(long progress) {
        return new GrowthTool(toolId, ownerUuid, ownerName, epoch, progress, branch, status);
    }

    public GrowthTool withStatus(Status status) {
        return new GrowthTool(toolId, ownerUuid, ownerName, epoch, progress, branch, status);
    }

    public GrowthTool withEpoch(int epoch) {
        return new GrowthTool(toolId, ownerUuid, ownerName, epoch, progress, branch, status);
    }

    public GrowthTool withBranch(EvolutionPlan.Branch branch) {
        return new GrowthTool(toolId, ownerUuid, ownerName, epoch, progress, branch, status);
    }
}
