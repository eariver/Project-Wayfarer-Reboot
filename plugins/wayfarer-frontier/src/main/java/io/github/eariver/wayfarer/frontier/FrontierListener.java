package io.github.eariver.wayfarer.frontier;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.frontier.item.ThemeItems;
import io.github.eariver.wayfarer.frontier.launchpad.LaunchpadManager;
import io.github.eariver.wayfarer.frontier.waystone.WaystoneManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Frontier gameplay listener: first-entry loadout, theme item boundaries, launchpad
 * placement/use, waystone interaction, and gate plates.
 */
public final class FrontierListener implements Listener {

    private final WayfarerFrontierPlugin plugin;
    private final Map<UUID, Long> lastLaunchpadUse = new HashMap<>();

    public FrontierListener(WayfarerFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.worldsBeyond().contains(player.getWorld().getName())) {
            plugin.themeItems().grantLoadoutOnEntry(player);
        }
        // keep players inside the gate world until they pick a theme (prevents void falls)
        if (player.getWorld().getName().equals(plugin.gateWorld())) {
            player.teleport(plugin.gateArrival());
            player.setGameMode(GameMode.ADVENTURE);
        }
        plugin.navGui().ensureNavigationItem(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastLaunchpadUse.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        ThemeItems.Identity id = plugin.themeItems().identify(item);
        if (id != null && !id.owner().equals(player.getUniqueId().toString())) {
            event.setCancelled(true);
            player.sendMessage(Text.mm("<red>This item is bound to another player."));
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (id != null) {
                switch (id.type()) {
                    case NAVIGATION -> {
                        event.setCancelled(true);
                        plugin.navGui().open(player);
                    }
                    case WAYSTONE_TOOL -> {
                        if (event.getClickedBlock() != null) {
                            event.setCancelled(true);
                            plugin.waystones().attemptPlacement(player, event.getClickedBlock());
                        }
                    }
                    case LAUNCHPAD -> {
                        if (event.getClickedBlock() != null) {
                            event.setCancelled(true);
                            plugin.launchpads().attemptPlacement(player, event.getClickedBlock(), item);
                        }
                    }
                    default -> {
                        // grapple hook keeps its own native behaviour
                    }
                }
            }
            // gate plates are handled by move events, not this
        }
        // waystone core interaction (no custom item required)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (plugin.waystones().isWaystoneCore(event.getClickedBlock())) {
                event.setCancelled(true);
                plugin.waystones().interactCore(player, event.getClickedBlock());
            }
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.launchpads().isLaunchpad(event.getBlock())) {
            event.setDropItems(false);
            plugin.launchpads().removeByBlock(event.getBlock(), event.getPlayer().getUniqueId(), true);
        }
        if (plugin.waystones().isWaystoneBlock(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Text.mm("<red>The waystone is protected."));
        }
    }

    @org.bukkit.event.EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        // C2/P-3: the loadout is only granted inside the configured Worlds Beyond world.
        if (plugin.worldsBeyond().contains(event.getPlayer().getWorld().getName())) {
            plugin.themeItems().grantLoadoutOnEntry(event.getPlayer());
        }
    }

    @org.bukkit.event.EventHandler
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        handleGate(event.getTo(), event.getPlayer());
    }

    /**
     * A-02/a-03: a gate/launchpad only needs checking when the block UNDER the player
     * changes, so a pure rotation or a same-block move does zero DB work. Pure teleport
     * events are handled by {@link #onTeleport} instead of twice.
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return; // same block (rotation or micro-move): nothing to re-check
        }
        if (event instanceof org.bukkit.event.player.PlayerTeleportEvent) return; // handled by onTeleport
        handleGate(to, event.getPlayer());
    }

    private void handleGate(Location to, Player player) {
        if (to == null || to.getWorld() == null) return;
        Block below = to.clone().subtract(0, 1, 0).getBlock();
        if (plugin.launchpads().isLaunchpad(below)) {
            long now = System.currentTimeMillis();
            Long last = lastLaunchpadUse.get(player.getUniqueId());
            if (player.isSneaking()) return;
            if (last != null && now - last < 2000) return;
            lastLaunchpadUse.put(player.getUniqueId(), now);
            plugin.launchpads().use(player, below);
            return;
        }
        if (plugin.gateWorld().equals(to.getWorld().getName())) {
            String gate = plugin.gatePlateGate(below);
            if (gate != null) {
                // land on safe ground like the theme teleports do (never buried in terrain)
                if (plugin.safeTeleport(player, gate)) {
                    Location arrival = plugin.arrival(gate);
                    if (arrival != null) player.playSound(arrival, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    player.sendMessage(Text.mm("<green>Traveling to the " + gate + " gate."));
                } else {
                    player.sendMessage(Text.mm("<red>The " + gate + " gate is not available."));
                }
            }
        }
    }
}
