package io.github.eariver.wayfarer.main;

import io.github.eariver.wayfarer.common.Db;
import io.github.eariver.wayfarer.main.domain.EvolutionPlan;
import io.github.eariver.wayfarer.main.domain.GrowthTool;
import io.github.eariver.wayfarer.main.domain.RepairPricing;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** H2-backed persistence for Growth Pickaxe tools. */
public final class GrowthRepository {

    private final Db db;

    public GrowthRepository(File file) {
        this.db = new Db(file);
        db.exec("""
                CREATE TABLE IF NOT EXISTS growth_tool (
                    tool_id VARCHAR(36) PRIMARY KEY,
                    owner_uuid VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(64) NOT NULL,
                    epoch INT NOT NULL DEFAULT 0,
                    progress BIGINT NOT NULL DEFAULT 0,
                    branch VARCHAR(16) NOT NULL DEFAULT 'FORTUNE',
                    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )""",
                "CREATE INDEX IF NOT EXISTS growth_tool_owner ON growth_tool(owner_uuid, status)");
    }

    public Optional<GrowthTool> findActive(UUID owner) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT tool_id, owner_uuid, owner_name, epoch, progress, branch, status FROM growth_tool " +
                            "WHERE owner_uuid=? AND status<>'REVOKED' ORDER BY epoch DESC")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(read(rs));
                    return Optional.empty();
                }
            }
        });
    }

    public List<GrowthTool> history(UUID owner) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT tool_id, owner_uuid, owner_name, epoch, progress, branch, status FROM growth_tool " +
                            "WHERE owner_uuid=? ORDER BY epoch DESC")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<GrowthTool> out = new ArrayList<>();
                    while (rs.next()) out.add(read(rs));
                    return out;
                }
            }
        });
    }

    public void insert(GrowthTool tool) {
        db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO growth_tool(tool_id, owner_uuid, owner_name, epoch, progress, branch, status, created_at, updated_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?)")) {
                long now = now();
                ps.setString(1, tool.toolId().toString());
                ps.setString(2, tool.ownerUuid().toString());
                ps.setString(3, tool.ownerName());
                ps.setInt(4, tool.epoch());
                ps.setLong(5, tool.progress());
                ps.setString(6, tool.branch().name());
                ps.setString(7, tool.status().name());
                ps.setLong(8, now);
                ps.setLong(9, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void update(GrowthTool tool) {
        db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE growth_tool SET owner_name=?, epoch=?, progress=?, branch=?, status=?, updated_at=? WHERE tool_id=?")) {
                ps.setString(1, tool.ownerName());
                ps.setInt(2, tool.epoch());
                ps.setLong(3, tool.progress());
                ps.setString(4, tool.branch().name());
                ps.setString(5, tool.status().name());
                ps.setLong(6, now());
                ps.setString(7, tool.toolId().toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void close() {
        db.close();
    }

    private static GrowthTool read(ResultSet rs) throws SQLException {
        return new GrowthTool(
                UUID.fromString(rs.getString("tool_id")),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getInt("epoch"),
                rs.getLong("progress"),
                EvolutionPlan.Branch.valueOf(rs.getString("branch")),
                GrowthTool.Status.valueOf(rs.getString("status")));
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    public static EvolutionPlan evolutionFromConfig(org.bukkit.configuration.ConfigurationSection s) {
        if (s == null) return EvolutionPlan.defaults();
        return new EvolutionPlan(
                s.getInt("stone", 100),
                s.getInt("iron", 400),
                s.getInt("diamond", 1200),
                s.getInt("post-base", 800),
                s.getInt("post-linear", 200),
                s.getInt("post-quadratic", 40));
    }

    public static RepairPricing repairFromConfig(org.bukkit.configuration.ConfigurationSection s) {
        if (s == null) return RepairPricing.defaults();
        return new RepairPricing(
                s.getDouble("base-cost", 100),
                s.getDouble("evolution-multiplier", 0.08),
                s.getDouble("minimum-charge-ratio", 0.25),
                s.getDouble("broken-flat-surcharge", 100),
                s.getDouble("broken-surcharge-per-evolution", 5));
    }
}
