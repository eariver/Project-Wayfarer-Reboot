package io.github.eariver.wayfarer.frontier;

import io.github.eariver.wayfarer.common.Db;
import io.github.eariver.wayfarer.frontier.cmd.FrontierCommand;
import io.github.eariver.wayfarer.frontier.gui.FrontierNavGui;
import io.github.eariver.wayfarer.frontier.gui.ShopGui;
import io.github.eariver.wayfarer.frontier.gui.WaystoneGui;
import io.github.eariver.wayfarer.frontier.item.ThemeItems;
import io.github.eariver.wayfarer.frontier.launchpad.LaunchpadManager;
import io.github.eariver.wayfarer.frontier.waystone.WaystoneManager;
import org.bukkit.Bukkit;
import io.github.eariver.wayfarer.common.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wayfarer_Frontier — Worlds Beyond / Ruined Frontier gameplay for the Frontier server.
 *
 * <p>Owns: theme boundaries, navigation GUI, traversal loadout (elytra/grapple hook/
 * launchpads), launchpad lifecycle, waystone network, and the gate structures in the
 * void gate world. Inventory group separation itself is delegated to
 * Multiverse-Inventories (this plugin never persists theme inventory contents).
 */
public final class WayfarerFrontierPlugin extends JavaPlugin {

    private static final int GATE_WORLDS_BEYOND_OFFSET = 5;
    private static final int GATE_RUINED_OFFSET = -5;

    private Set<String> worldsBeyond;
    private Set<String> ruinedFrontier;
    private String gateWorld;
    private Map<String, Location> arrivals;
    private Db db;
    private ThemeItems themeItems;
    private LaunchpadManager launchpads;
    private WaystoneManager waystones;
    private FrontierNavGui navGui;
    private ShopGui shopGui;
    private WaystoneGui waystoneGui;
    private boolean grappleAvailable;
    private final java.util.Map<String, org.bukkit.block.Block> gatePlates = new java.util.HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration cfg = getConfig();
        worldsBeyond = new HashSet<>(cfg.getStringList("themes.worlds-beyond"));
        ruinedFrontier = new HashSet<>(cfg.getStringList("themes.ruined-frontier"));
        gateWorld = cfg.getString("themes.gate-world", "world");
        arrivals = new HashMap<>();
        for (String key : List.of("gate", "worlds-beyond", "ruined-frontier")) {
            var section = cfg.getConfigurationSection("arrivals." + key);
            if (section != null) {
                World w = Bukkit.getWorld(section.getString("world", gateWorld));
                if (w != null) {
                    arrivals.put(key, new Location(w,
                            section.getDouble("x"), section.getDouble("y"), section.getDouble("z")));
                }
            }
        }

        db = new Db(new File(getDataFolder(), "frontier.db"));
        db.exec("""
                CREATE TABLE IF NOT EXISTS loadout (
                    player_uuid VARCHAR(36) NOT NULL,
                    item_type VARCHAR(32) NOT NULL,
                    epoch INT NOT NULL DEFAULT 0,
                    granted_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, item_type))""",
                """
                CREATE TABLE IF NOT EXISTS launchpad (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    world VARCHAR(64) NOT NULL,
                    x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                    owner_uuid VARCHAR(36) NOT NULL,
                    yaw FLOAT NOT NULL,
                    uses_left INT NOT NULL,
                    created_at BIGINT NOT NULL,
                    last_used_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL)""",
                """
                CREATE TABLE IF NOT EXISTS waystone (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    world VARCHAR(64) NOT NULL,
                    x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                    founder_uuid VARCHAR(36) NOT NULL,
                    founder_name VARCHAR(64) NOT NULL,
                    maintainer_uuid VARCHAR(36) NOT NULL,
                    maintainer_name VARCHAR(64) NOT NULL,
                    biome VARCHAR(64) NOT NULL,
                    sequence INT NOT NULL,
                    state VARCHAR(16) NOT NULL,
                    protected_until BIGINT NOT NULL,
                    contestable_until BIGINT NOT NULL,
                    dormant_until BIGINT NOT NULL,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL)""",
                """
                CREATE TABLE IF NOT EXISTS waystone_discovery (
                    player_uuid VARCHAR(36) NOT NULL,
                    waystone_id BIGINT NOT NULL,
                    discovered_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, waystone_id))""");

        themeItems = new ThemeItems(this);
        launchpads = new LaunchpadManager(this, db);
        waystones = new WaystoneManager(this, db);
        navGui = new FrontierNavGui(this);
        shopGui = new ShopGui(this);
        waystoneGui = new WaystoneGui(this);

        grappleAvailable = themeItems.probeGrapple();
        buildGatesIfNeeded();
        getServer().getPluginManager().registerEvents(new FrontierListener(this), this);
        getServer().getPluginCommand("wayfarer-frontier").setExecutor(new FrontierCommand(this));
        // reap expired waystones/launchpads every 10 minutes
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                waystones.reapExpired();
                launchpads.reapExpired();
            } catch (Exception e) {
                getLogger().warning("reaper failed: " + e.getMessage());
            }
        }, 12000L, 12000L);
        getLogger().info("Wayfarer_Frontier ready (worldsBeyond=" + worldsBeyond + ", ruined=" + ruinedFrontier
                + ", grapple=" + grappleAvailable + ")");
    }

    @Override
    public void onDisable() {
        if (db != null) db.close();
    }

    public Set<String> worldsBeyond() {
        return worldsBeyond;
    }

    public Set<String> ruinedFrontier() {
        return ruinedFrontier;
    }

    public String gateWorld() {
        return gateWorld;
    }

    public Db db() {
        return db;
    }

    public ThemeItems themeItems() {
        return themeItems;
    }

    public LaunchpadManager launchpads() {
        return launchpads;
    }

    public WaystoneManager waystones() {
        return waystones;
    }

    public FrontierNavGui navGui() {
        return navGui;
    }

    public ShopGui shopGui() {
        return shopGui;
    }

    public WaystoneGui waystoneGui() {
        return waystoneGui;
    }

    public boolean grappleAvailable() {
        return grappleAvailable;
    }

    public NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    public Location arrival(String key) {
        return arrivals.get(key);
    }

    /**
     * C2/P-6: teleport to a theme arrival, but land on safe ground. The configured arrival
     * is a hint — the actual landing Y is the first solid block with two clear blocks above
     * it at the target column, never inside terrain or the void.
     */
    public boolean safeTeleport(Player player, String key) {
        Location hint = arrivals.get(key);
        if (hint == null || hint.getWorld() == null) return false;
        Location safe = safeGround(hint);
        if (safe == null) {
            player.sendMessage(Text.mm("<red>No safe arrival point in <theme>.", "theme", key));
            return false;
        }
        player.teleport(safe);
        return true;
    }

    private Location safeGround(Location hint) {
        var world = hint.getWorld();
        int x = hint.getBlockX();
        int z = hint.getBlockZ();
        int max = world.getMaxHeight() - 3;
        int start = Math.min(hint.getBlockY(), max);
        for (int y = start; y > world.getMinHeight(); y--) {
            var ground = world.getBlockAt(x, y, z);
            if (ground.getType().isSolid()
                    && world.getBlockAt(x, y + 1, z).getType().isAir()
                    && world.getBlockAt(x, y + 2, z).getType().isAir()) {
                return new Location(world, x + 0.5, y + 1.0, z + 0.5);
            }
        }
        // fall back to the configured hint (already air) rather than burying the player
        return hint.getBlock().getType().isAir() ? hint : null;
    }

    /** Build the two physical gate structures in the gate world if missing. */
    public void buildGatesIfNeeded() {
        org.bukkit.World world = Bukkit.getWorld(gateWorld);
        if (world == null) return;
        buildGate(world, "worlds-beyond", GATE_WORLDS_BEYOND_OFFSET);
        buildGate(world, "ruined-frontier", GATE_RUINED_OFFSET);
    }

    private void buildGate(org.bukkit.World world, String theme, int offset) {
        int y = 63;
        int x = offset;
        int z = 0;
        org.bukkit.block.Block plate = world.getBlockAt(x, y, z);
        if (plate.getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            // simple gate: two quartz pillars, a sign, and the trigger plate
            for (int h = 1; h <= 3; h++) {
                world.getBlockAt(x - 1, y + h, z).setType(Material.QUARTZ_PILLAR);
                world.getBlockAt(x + 1, y + h, z).setType(Material.QUARTZ_PILLAR);
            }
            world.getBlockAt(x - 1, y + 4, z).setType(Material.QUARTZ_SLAB);
            world.getBlockAt(x, y + 4, z).setType(Material.QUARTZ_SLAB);
            world.getBlockAt(x + 1, y + 4, z).setType(Material.QUARTZ_SLAB);
            world.getBlockAt(x, y + 3, z).setType(Material.OAK_SIGN);
            plate.setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        }
        gatePlates.put(theme, plate);
    }

    /** Theme for the plate the player is standing on (null when not a gate plate). */
    public String gatePlateGate(org.bukkit.block.Block block) {
        for (var entry : gatePlates.entrySet()) {
            if (entry.getValue().equals(block)) return entry.getKey();
        }
        return null;
    }

    /** Gate placement (bot should never idle in the gate world silently). */
    public Location gateArrival() {
        Location l = arrivals.get("gate");
        return l != null ? l : new Location(Bukkit.getWorld(gateWorld), 0.5, 64, 0.5);
    }

    public FileConfiguration conf() {
        return getConfig();
    }

    public long now() {
        return System.currentTimeMillis() / 1000L;
    }
}
