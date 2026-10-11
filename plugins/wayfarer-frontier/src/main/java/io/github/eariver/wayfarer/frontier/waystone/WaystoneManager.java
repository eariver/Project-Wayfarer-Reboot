package io.github.eariver.wayfarer.frontier.waystone;

import io.github.eariver.wayfarer.common.Db;
import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.frontier.WayfarerFrontierPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Waystone network lifecycle. A waystone is a 5x5 structure with a core block; the
 * record (founder, maintainer, biome, sequence, state, deadlines) is authoritative
 * in H2; the structure is rebuilt from the origin when needed.
 *
 * <p>States: PROTECTED (-&gt;contestable_until) CONTESTABLE (-&gt;dormant_until) DORMANT
 * (-&gt;ruin) RUINED (record kept, structure removed).
 */
public final class WaystoneManager {

    public enum State { PROTECTED, CONTESTABLE, DORMANT, RUINED }

    public record Waystone(long id, String world, int x, int y, int z,
                           UUID founderUuid, String founderName,
                           UUID maintainerUuid, String maintainerName,
                           String biome, int sequence, State state,
                           long protectedUntil, long contestableUntil, long dormantUntil) {
    }

    private final WayfarerFrontierPlugin plugin;
    private final Db db;
    private final Material coreMaterial;
    private final List<Material> palette;
    private final long protectedDays;
    private final long contestableDays;
    private final long dormantDays;
    private final int minDistance;
    private final int placementPrice;
    private final int maintainPrice;
    private final int overwritePrice;
    private final int revivePrice;

    public WaystoneManager(WayfarerFrontierPlugin plugin, Db db) {
        this.plugin = plugin;
        this.db = db;
        var cfg = plugin.conf().getConfigurationSection("waystone");
        coreMaterial = Material.valueOf(cfg.getString("core-material", "LODESTONE"));
        palette = new ArrayList<>();
        for (String m : cfg.getStringList("palette")) palette.add(Material.valueOf(m));
        protectedDays = cfg.getLong("protected-days", 14);
        contestableDays = cfg.getLong("contestable-days", 14);
        dormantDays = cfg.getLong("dormant-days", 180);
        minDistance = cfg.getInt("min-horizontal-distance", 1000);
        placementPrice = cfg.getInt("placement-price", 600);
        maintainPrice = cfg.getInt("maintain-price", 200);
        overwritePrice = cfg.getInt("overwrite-price", 300);
        revivePrice = cfg.getInt("revive-price", 150);
    }

    public int placementPrice() {
        return placementPrice;
    }

    // ---------- structure helpers ----------

    public boolean isWaystoneBlock(Block block) {
        return block != null && findAt(block.getLocation()) != null;
    }

    public boolean isWaystoneCore(Block block) {
        return block != null && block.getType() == coreMaterial && findAt(block.getLocation()) != null;
    }

    public Waystone findAt(Location location) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM waystone WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, location.getWorld().getName());
                ps.setInt(2, location.getBlockX());
                ps.setInt(3, location.getBlockY());
                ps.setInt(4, location.getBlockZ());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? read(rs) : null;
                }
            }
        });
    }

    private Waystone read(ResultSet rs) throws Exception {
        return new Waystone(rs.getLong("id"), rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                UUID.fromString(rs.getString("founder_uuid")), rs.getString("founder_name"),
                UUID.fromString(rs.getString("maintainer_uuid")), rs.getString("maintainer_name"),
                rs.getString("biome"), rs.getInt("sequence"),
                State.valueOf(rs.getString("state")), rs.getLong("protected_until"),
                rs.getLong("contestable_until"), rs.getLong("dormant_until"));
    }

    private void buildStructure(Waystone waystone, Player maintainer) {
        WorldReference w = new WorldReference(plugin.getServer().getWorld(waystone.world()));
        if (w.world == null) return;
        Material base = palette.isEmpty() ? Material.STONE_BRICKS : palette.get(0);
        int x = waystone.x(), y = waystone.y(), z = waystone.z();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.world.getBlockAt(x + dx, y, z + dz).setType(base);
            }
        }
        w.world.getBlockAt(x, y + 1, z).setType(coreMaterial);
        Block head = w.world.getBlockAt(x, y + 2, z);
        head.setType(Material.PLAYER_HEAD);
        if (head.getState() instanceof org.bukkit.block.Skull skull) {
            skull.setOwningPlayer(plugin.getServer().getOfflinePlayer(maintainer.getUniqueId()));
            skull.update(true, false);
        }
        w.world.getBlockAt(x + 2, y + 1, z).setType(Material.LANTERN);
        w.world.getBlockAt(x - 2, y + 1, z).setType(Material.LANTERN);
        w.world.getBlockAt(x, y + 3, z).setType(palette.size() > 1 ? palette.get(1) : base);
    }

    private record WorldReference(org.bukkit.World world) {
    }

    public void removeStructure(Waystone waystone) {
        var w = plugin.getServer().getWorld(waystone.world());
        if (w == null) return;
        int x = waystone.x(), y = waystone.y(), z = waystone.z();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(x + dx, y, z + dz).setType(Material.AIR);
                w.getBlockAt(x + dx, y + 1, z + dz).setType(Material.AIR);
            }
        }
        w.getBlockAt(x, y + 2, z).setType(Material.AIR);
        w.getBlockAt(x, y + 3, z).setType(Material.AIR);
    }

    // ---------- placement ----------

    public void attemptPlacement(Player player, Block clicked) {
        if (!plugin.worldsBeyond().contains(clicked.getWorld().getName())) {
            player.sendMessage(Text.mm("<red>Waystones can only be placed in Worlds Beyond."));
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        var id = plugin.themeItems().identify(tool);
        if (id == null || id.type() != io.github.eariver.wayfarer.frontier.item.ThemeItems.ItemType.WAYSTONE_TOOL
                || !id.owner().equals(player.getUniqueId().toString())) {
            player.sendMessage(Text.mm("<red>Hold your Waystone Placement Tool."));
            return;
        }
        Location loc = clicked.getRelative(BlockFace.UP).getLocation();
        // placement checks: air volume, no overlap, distance, height range
        if (!loc.getBlock().getType().isAir() || !loc.clone().add(0, 1, 0).getBlock().getType().isAir()) {
            player.sendMessage(Text.mm("<red>Not enough air to place the waystone."));
            return;
        }
        if (isWaystoneBlock(loc.getBlock())) {
            player.sendMessage(Text.mm("<red>There is already a waystone here."));
            return;
        }
        List<Waystone> active = activeWaystones(player.getWorld().getName());
        for (Waystone w : active) {
            double dist = Math.hypot(w.x() - loc.getBlockX(), w.z() - loc.getBlockZ());
            if (dist < minDistance) {
                player.sendMessage(Text.mm("<red>Too close to another waystone (min <min> blocks).",
                        "min", String.valueOf(minDistance)));
                return;
            }
        }
        if (loc.getWorld().getMinHeight() > loc.getBlockY() || loc.getBlockY() + 3 > loc.getWorld().getMaxHeight()) {
            player.sendMessage(Text.mm("<red>Waystone does not fit world height."));
            return;
        }
        String biome = loc.getBlock().getBiome().getKey().toString();
        long now = plugin.now();
        long sequence = nextSequence(player.getUniqueId(), biome);
        long id2 = db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO waystone(world, x, y, z, founder_uuid, founder_name, maintainer_uuid, maintainer_name, " +
                            "biome, sequence, state, protected_until, contestable_until, dormant_until, created_at, updated_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, player.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setString(5, player.getUniqueId().toString());
                ps.setString(6, player.getName());
                ps.setString(7, player.getUniqueId().toString());
                ps.setString(8, player.getName());
                ps.setString(9, biome);
                ps.setInt(10, (int) sequence);
                ps.setString(11, State.PROTECTED.name());
                ps.setLong(12, now + protectedDays * 86400);
                ps.setLong(13, now + (protectedDays + contestableDays) * 86400);
                ps.setLong(14, now + (protectedDays + contestableDays + dormantDays) * 86400);
                ps.setLong(15, now);
                ps.setLong(16, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        });
        Waystone waystone = db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM waystone WHERE id=?")) {
                ps.setLong(1, id2);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? read(rs) : null;
                }
            }
        });
        buildStructure(waystone, player);
        discover(player.getUniqueId(), id2);
        tool.setAmount(tool.getAmount() - 1);
        player.sendMessage(Text.mm("<green>Waystone founded: <name>'s <biome> #<seq>.",
                "name", player.getName(), "biome", prettyBiome(biome), "seq", String.valueOf(sequence)));
    }

    private String prettyBiome(String biome) {
        String path = biome.substring(biome.indexOf(':') + 1).replace('_', ' ');
        String[] parts = path.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private long nextSequence(UUID founder, String biome) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM waystone WHERE founder_uuid=? AND biome=?")) {
                ps.setString(1, founder.toString());
                ps.setString(2, biome);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1) + 1;
                }
            }
        });
    }

    public List<Waystone> activeWaystones(String world) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM waystone WHERE world=? AND state<>'RUINED'")) {
                ps.setString(1, world);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Waystone> out = new ArrayList<>();
                    while (rs.next()) out.add(read(rs));
                    return out;
                }
            }
        });
    }

    public List<Waystone> teleportable(UUID player, String world) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT w.* FROM waystone w JOIN waystone_discovery d ON d.waystone_id=w.id " +
                            "WHERE w.world=? AND w.state IN ('PROTECTED','CONTESTABLE') AND d.player_uuid=?")) {
                ps.setString(1, world);
                ps.setString(2, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Waystone> out = new ArrayList<>();
                    while (rs.next()) out.add(read(rs));
                    return out;
                }
            }
        });
    }

    public void discover(UUID player, long waystoneId) {
        db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "MERGE INTO waystone_discovery(player_uuid, waystone_id, discovered_at) KEY(player_uuid, waystone_id) VALUES(?,?,?)")) {
                ps.setString(1, player.toString());
                ps.setLong(2, waystoneId);
                ps.setLong(3, plugin.now());
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ---------- interactions ----------

    public void interactCore(Player player, Block core) {
        Waystone waystone = findAt(core.getLocation());
        if (waystone == null) return;
        discover(player.getUniqueId(), waystone.id());
        State effective = effectiveState(waystone, plugin.now());
        if (effective == State.RUINED) {
            player.sendMessage(Text.mm("<red>This waystone is ruined."));
            return;
        }
        if (effective == State.DORMANT) {
            player.sendMessage(Text.mm("<gold>This waystone is dormant. You may revive it for <price> WM (/wayfarer-frontier revive).",
                    "price", String.valueOf(revivePrice)));
            plugin.waystoneGui().openWaystone(player, waystone.id());
            return;
        }
        player.sendMessage(Text.mm("<green>Discovered <name>.", "name", displayName(waystone)));
        plugin.waystoneGui().openWaystone(player, waystone.id());
    }

    public String displayName(Waystone waystone) {
        return waystone.founderName() + "'s " + prettyBiome(waystone.biome()) + " #" + waystone.sequence();
    }

    /** Time-aware state (records store the initial PROTECTED deadlines). */
    public State effectiveState(Waystone waystone, long now) {
        if (waystone.state() == State.RUINED) return State.RUINED;
        if (now <= waystone.protectedUntil()) return State.PROTECTED;
        if (now <= waystone.contestableUntil()) return State.CONTESTABLE;
        if (now <= waystone.dormantUntil()) return State.DORMANT;
        return State.RUINED;
    }

    public boolean teleportTo(Player player, long waystoneId) {
        Waystone waystone = byId(waystoneId);
        if (waystone == null) return false;
        if (effectiveState(waystone, plugin.now()) == State.DORMANT
                || effectiveState(waystone, plugin.now()) == State.RUINED) {
            player.sendMessage(Text.mm("<red>That waystone is not usable."));
            return false;
        }
        boolean known = db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM waystone_discovery WHERE player_uuid=? AND waystone_id=?")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setLong(2, waystoneId);
                return ps.executeQuery().next();
            }
        });
        if (!known) {
            player.sendMessage(Text.mm("<red>You have not discovered that waystone."));
            return false;
        }
        Location target = new Location(plugin.getServer().getWorld(waystone.world()),
                waystone.x() + 0.5, waystone.y() + 1, waystone.z() + 0.5);
        player.teleport(target);
        return true;
    }

    public Waystone byId(long id) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM waystone WHERE id=?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? read(rs) : null;
                }
            }
        });
    }

    /** On-site maintain (200) / overwrite (300) / revive (150) from the core. */
    public boolean lifecycleOperation(Player player, Block core, String operation) {
        Waystone waystone = findAt(core.getLocation());
        if (waystone == null) return false;
        long now = plugin.now();
        State state = effectiveState(waystone, now);
        int price = switch (operation) {
            case "maintain" -> maintainPrice;
            case "overwrite" -> overwritePrice;
            case "revive" -> revivePrice;
            default -> -1;
        };
        if (price < 0) return false;
        boolean allowed = switch (operation) {
            case "maintain" -> waystone.maintainerUuid().equals(player.getUniqueId())
                    && (state == State.PROTECTED || state == State.CONTESTABLE);
            case "overwrite" -> state == State.CONTESTABLE;
            case "revive" -> state == State.DORMANT;
            default -> false;
        };
        if (!allowed) {
            player.sendMessage(Text.mm("<red>That operation is not available here now."));
            return false;
        }
        if (!charge(player, price)) {
            player.sendMessage(Text.mm("<red>You need <price> WM.", "price", String.valueOf(price)));
            return false;
        }
        long p = now + protectedDays * 86400;
        long c2 = now + (protectedDays + contestableDays) * 86400;
        long d2 = now + (protectedDays + contestableDays + dormantDays) * 86400;
        db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE waystone SET maintainer_uuid=?, maintainer_name=?, state=?, protected_until=?, contestable_until=?, dormant_until=?, updated_at=? WHERE id=?")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, State.PROTECTED.name());
                ps.setLong(4, p);
                ps.setLong(5, c2);
                ps.setLong(6, d2);
                ps.setLong(7, now);
                ps.setLong(8, waystone.id());
                ps.executeUpdate();
            }
            return null;
        });
        Waystone updated = byId(waystone.id());
        buildStructure(updated, player);
        player.sendMessage(Text.mm("<green>Waystone <op> done for <price> WM.", "op", operation, "price", String.valueOf(price)));
        return true;
    }

    private boolean charge(Player player, long price) {
        try {
            var economy = plugin.getServer().getServicesManager()
                    .getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (economy == null) return false;
            var provider = economy.getProvider();
            if (!provider.has(player, price)) return false;
            return provider.withdrawPlayer(player, price).transactionSuccess();
        } catch (Exception e) {
            plugin.getLogger().warning("economy charge failed: " + e);
            return false;
        }
    }

    public void reapExpired() {
        long now = plugin.now();
        List<Waystone> ruined = db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM waystone WHERE state<>'RUINED' AND dormant_until<?")) {
                ps.setLong(1, now);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Waystone> out = new ArrayList<>();
                    while (rs.next()) out.add(read(rs));
                    return out;
                }
            }
        });
        for (Waystone waystone : ruined) {
            db.tx(c -> {
                try (PreparedStatement ps = c.prepareStatement("UPDATE waystone SET state='RUINED' WHERE id=?")) {
                    ps.setLong(1, waystone.id());
                    ps.executeUpdate();
                }
                return null;
            });
            removeStructure(waystone);
            plugin.getLogger().info("waystone " + waystone.id() + " ruined by expiry");
        }
    }

    /** Admin/verification hook: force a waystone's deadlines (relative days from now). */
    public boolean forceState(long id, long protectedDaysAhead, long contestableDaysAhead, long dormantDaysAhead) {
        long now = plugin.now();
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE waystone SET protected_until=?, contestable_until=?, dormant_until=?, updated_at=? WHERE id=?")) {
                ps.setLong(1, now + protectedDaysAhead * 86400);
                ps.setLong(2, now + contestableDaysAhead * 86400);
                ps.setLong(3, now + dormantDaysAhead * 86400);
                ps.setLong(4, now);
                ps.setLong(5, id);
                return ps.executeUpdate() > 0;
            }
        });
    }
}
