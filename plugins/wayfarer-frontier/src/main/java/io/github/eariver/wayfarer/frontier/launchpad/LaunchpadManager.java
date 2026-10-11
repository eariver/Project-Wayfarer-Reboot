package io.github.eariver.wayfarer.frontier.launchpad;

import io.github.eariver.wayfarer.common.Db;
import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.frontier.WayfarerFrontierPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Launchpad lifecycle: place, launch, expires, manual break (any player), system reap. */
public final class LaunchpadManager {

    private final WayfarerFrontierPlugin plugin;
    private final Db db;
    private final Material material;
    private final int maxUses;
    private final int expireDays;
    private final double horizontal;
    private final double vertical;
    private final boolean autoElytra;

    public LaunchpadManager(WayfarerFrontierPlugin plugin, Db db) {
        this.plugin = plugin;
        this.db = db;
        var cfg = plugin.conf().getConfigurationSection("launchpad");
        material = Material.valueOf(cfg.getString("material", "LIGHT_WEIGHTED_PRESSURE_PLATE"));
        maxUses = cfg.getInt("max-uses", 3);
        expireDays = cfg.getInt("expire-days", 30);
        horizontal = cfg.getDouble("horizontal-velocity", 2.5);
        vertical = cfg.getDouble("vertical-velocity", 1.2);
        autoElytra = cfg.getBoolean("auto-deploy-elytra", true);
    }

    public boolean isLaunchpad(Block block) {
        return block != null && block.getType() == material && registeredAt(block.getLocation());
    }

    private boolean registeredAt(Location location) {
        return db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM launchpad WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, location.getWorld().getName());
                ps.setInt(2, location.getBlockX());
                ps.setInt(3, location.getBlockY());
                ps.setInt(4, location.getBlockZ());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /** Attempt to place a launchpad from a held item. Consumes the item on success only. */
    public void attemptPlacement(Player player, Block clicked, ItemStack item) {
        var id = plugin.themeItems().identify(item);
        if (id == null) return;
        if (!plugin.themeItems().themeWorld(clicked.getWorld())) {
            player.sendMessage(Text.mm("<red>Launchpads only work inside Frontier themes."));
            return;
        }
        Block target = clicked.getRelative(0, 1, 0);
        if (!target.getType().isAir()) {
            player.sendMessage(Text.mm("<red>No space to place the launchpad."));
            return;
        }
        if (clicked.getType().isAir() || !clicked.getType().isSolid()) {
            player.sendMessage(Text.mm("<red>Place it on a solid block."));
            return;
        }
        if (isLaunchpad(target) || plugin.waystones().isWaystoneBlock(clicked)) {
            player.sendMessage(Text.mm("<red>Blocked position."));
            return;
        }
        long now = plugin.now();
        long expires = now + expireDays * 86400L;
        Location loc = target.getLocation();
        db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO launchpad(world, x, y, z, owner_uuid, yaw, uses_left, created_at, last_used_at, expires_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setString(5, player.getUniqueId().toString());
                ps.setFloat(6, player.getLocation().getYaw());
                ps.setInt(7, maxUses);
                ps.setLong(8, now);
                ps.setLong(9, now);
                ps.setLong(10, expires);
                ps.executeUpdate();
            }
            return null;
        });
        target.setType(material);
        item.setAmount(item.getAmount() - 1);
        player.sendMessage(Text.mm("<green>Launchpad placed (<uses> launches).", "uses", String.valueOf(maxUses)));
    }

    /** Launch the player who stepped on the pad. */
    public void use(Player player, Block pad) {
        Integer usesLeft = db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT uses_left FROM launchpad WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, pad.getWorld().getName());
                ps.setInt(2, pad.getX());
                ps.setInt(3, pad.getY());
                ps.setInt(4, pad.getZ());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : null;
                }
            }
        });
        if (usesLeft == null || usesLeft <= 0) return;
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        if (direction.lengthSquared() < 0.01) {
            direction = new Vector(0, 0, 1).rotateAroundY(Math.toRadians(pad.getY() - pad.getY() + player.getLocation().getYaw()));
        }
        Vector velocity = direction.multiply(horizontal).setY(vertical);
        player.setVelocity(velocity);
        if (autoElytra && player.getInventory().getChestplate() == null) {
            ItemStack elytra = plugin.themeItems().build(io.github.eariver.wayfarer.frontier.item.ThemeItems.ItemType.ELYTRA, player.getUniqueId(), "worlds_beyond", 0);
            var left = player.getInventory().addItem(elytra);
            left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }
        int remaining = usesLeft - 1;
        long now = plugin.now();
        db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE launchpad SET uses_left=?, last_used_at=?, expires_at=? WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setInt(1, remaining);
                ps.setLong(2, now);
                ps.setLong(3, now + expireDays * 86400L);
                ps.setString(4, pad.getWorld().getName());
                ps.setInt(5, pad.getX());
                ps.setInt(6, pad.getY());
                ps.setInt(7, pad.getZ());
                ps.executeUpdate();
            }
            return null;
        });
        player.sendMessage(Text.mm("<green>Launchpad fired! <uses> launch(es) left.", "uses", String.valueOf(remaining)));
        if (remaining <= 0) {
            removeByBlock(pad, player.getUniqueId(), false);
        }
    }

    public void removeByBlock(Block block, java.util.UUID actor, boolean manual) {
        boolean removed = db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM launchpad WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, block.getWorld().getName());
                ps.setInt(2, block.getX());
                ps.setInt(3, block.getY());
                ps.setInt(4, block.getZ());
                return ps.executeUpdate() > 0;
            }
        });
        if (removed && block.getType() == material) {
            block.setType(Material.AIR);
        }
        if (removed && manual) {
            plugin.getLogger().info("launchpad removed at " + block.getLocation() + " by " + actor);
        }
    }

    public void reapExpired() {
        long now = plugin.now();
        List<Location> doomed = db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT world, x, y, z FROM launchpad WHERE expires_at<? OR uses_left<=0")) {
                ps.setLong(1, now);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Location> out = new ArrayList<>();
                    while (rs.next()) {
                        var w = plugin.getServer().getWorld(rs.getString(1));
                        if (w != null) out.add(new Location(w, rs.getInt(2), rs.getInt(3), rs.getInt(4)));
                    }
                    return out;
                }
            }
        });
        for (Location loc : doomed) {
            db.tx(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM launchpad WHERE world=? AND x=? AND y=? AND z=?")) {
                    ps.setString(1, loc.getWorld().getName());
                    ps.setInt(2, loc.getBlockX());
                    ps.setInt(3, loc.getBlockY());
                    ps.setInt(4, loc.getBlockZ());
                    ps.executeUpdate();
                }
                return null;
            });
            if (loc.getWorld() != null && loc.getBlock().getType() == material) {
                loc.getBlock().setType(Material.AIR);
            }
        }
    }

    
}
