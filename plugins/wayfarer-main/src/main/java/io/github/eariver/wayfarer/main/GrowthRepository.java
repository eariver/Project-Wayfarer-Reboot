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

/**
 * H2-backed persistence for Growth Pickaxe tools.
 *
 * <p>P-10 fix: {@code tool_id} is the stable primary key of the logical tool. A reissue
 * bumps {@code epoch} in place via a conditional UPDATE, so a reissue can never delete
 * the logical tool or violate the primary key. Legacy rows are migrated in place
 * (netherite flag added with default false).
 */
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
                    netherite_upgraded BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )""",
                "CREATE INDEX IF NOT EXISTS growth_tool_owner ON growth_tool(owner_uuid, status)",
                "ALTER TABLE growth_tool ADD COLUMN IF NOT EXISTS netherite_upgraded BOOLEAN NOT NULL DEFAULT FALSE");
    }

    public Optional<GrowthTool> findActive(UUID owner) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT tool_id, owner_uuid, owner_name, epoch, progress, branch, status, netherite_upgraded " +
                            "FROM growth_tool WHERE owner_uuid=? AND status<>'REVOKED' ORDER BY epoch DESC")) {
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
                    "SELECT tool_id, owner_uuid, owner_name, epoch, progress, branch, status, netherite_upgraded " +
                            "FROM growth_tool WHERE owner_uuid=? ORDER BY epoch DESC")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<GrowthTool> out = new ArrayList<>();
                    while (rs.next()) out.add(read(rs));
                    return out;
                }
            }
        });
    }

    public GrowthTool findByToolId(UUID toolId) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT tool_id, owner_uuid, owner_name, epoch, progress, branch, status, netherite_upgraded " +
                            "FROM growth_tool WHERE tool_id=?")) {
                ps.setString(1, toolId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? read(rs) : null;
                }
            }
        });
    }

    public void insert(GrowthTool tool) {
        db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO growth_tool(tool_id, owner_uuid, owner_name, epoch, progress, branch, status, " +
                            "netherite_upgraded, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                long now = now();
                ps.setString(1, tool.toolId().toString());
                ps.setString(2, tool.ownerUuid().toString());
                ps.setString(3, tool.ownerName());
                ps.setInt(4, tool.epoch());
                ps.setLong(5, tool.progress());
                ps.setString(6, tool.branch().name());
                ps.setString(7, tool.status().name());
                ps.setBoolean(8, tool.netheriteUpgraded());
                ps.setLong(9, now);
                ps.setLong(10, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void update(GrowthTool tool) {
        db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE growth_tool SET owner_name=?, epoch=?, progress=?, branch=?, status=?, " +
                            "netherite_upgraded=?, updated_at=? WHERE tool_id=?")) {
                ps.setString(1, tool.ownerName());
                ps.setInt(2, tool.epoch());
                ps.setLong(3, tool.progress());
                ps.setString(4, tool.branch().name());
                ps.setString(5, tool.status().name());
                ps.setBoolean(6, tool.netheriteUpgraded());
                ps.setLong(7, now());
                ps.setString(8, tool.toolId().toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * P-10: conditional epoch bump. Returns the number of affected rows (0 when the
     * expected epoch no longer matches, i.e. another reissue already ran).
     */
    public int bumpEpoch(UUID toolId, int expectedEpoch) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE growth_tool SET epoch=epoch+1, updated_at=? WHERE tool_id=? AND epoch=? " +
                            "AND status IN ('ACTIVE','BROKEN')")) {
                ps.setLong(1, now());
                ps.setString(2, toolId.toString());
                ps.setInt(3, expectedEpoch);
                return ps.executeUpdate();
            }
        });
    }

    /** Conditional flag flip used by the WM-gated netherite upgrade; returns affected rows. */
    public int markNetheriteUpgraded(UUID toolId) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE growth_tool SET netherite_upgraded=TRUE, updated_at=? WHERE tool_id=? AND netherite_upgraded=FALSE")) {
                ps.setLong(1, now());
                ps.setString(2, toolId.toString());
                return ps.executeUpdate();
            }
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
                GrowthTool.Status.valueOf(rs.getString("status")),
                rs.getBoolean("netherite_upgraded"));
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
                s.getInt("enchant-base", 800),
                s.getInt("enchant-linear", 200),
                s.getInt("enchant-quadratic", 40));
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
