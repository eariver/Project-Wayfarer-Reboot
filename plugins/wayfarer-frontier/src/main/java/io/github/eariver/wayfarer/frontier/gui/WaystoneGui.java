package io.github.eariver.wayfarer.frontier.gui;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.frontier.WayfarerFrontierPlugin;
import io.github.eariver.wayfarer.frontier.waystone.WaystoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Waystone detail GUI: teleport plus on-site lifecycle operations. */
public final class WaystoneGui implements Listener {

    private final WayfarerFrontierPlugin plugin;

    public WaystoneGui(WayfarerFrontierPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openWaystone(Player player, long waystoneId) {
        var manager = plugin.waystones();
        WaystoneManager.Waystone waystone = manager.byId(waystoneId);
        if (waystone == null) return;
        WaystoneManager.State state = manager.effectiveState(waystone, plugin.now());
        WayHolder holder = new WayHolder(waystoneId);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Text.mm("<dark_gray>Waystone: " + manager.displayName(waystone)));
        holder.setInventory(inv);
        inv.setItem(4, headIcon(waystone));
        inv.setItem(11, button(Material.ENDER_PEARL, "<aqua>Teleport here",
                List.of("<gray>Teleports you to this waystone")));
        if (state == WaystoneManager.State.PROTECTED || state == WaystoneManager.State.CONTESTABLE) {
            if (waystone.maintainerUuid().equals(player.getUniqueId())) {
                inv.setItem(13, button(Material.HEART_OF_THE_SEA, "<gold>Maintain (200 WM)",
                        List.of("<gray>Refresh the waystone lifecycle")));
            }
        }
        if (state == WaystoneManager.State.CONTESTABLE) {
            inv.setItem(13, button(Material.IRON_AXE, "<red>Overwrite (300 WM)",
                    List.of("<gray>Become the maintainer")));
        }
        if (state == WaystoneManager.State.DORMANT) {
            inv.setItem(15, button(Material.AMETHYST_SHARD, "<light_purple>Revive (150 WM)",
                    List.of("<gray>Bring this dormant waystone back")));
        }
        player.openInventory(inv);
    }

    private ItemStack headIcon(WaystoneManager.Waystone waystone) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        meta.displayName(Text.mm("<gold>" + plugin.waystones().displayName(waystone)));
        meta.lore(List.of(
                Text.mm("<gray>Founder: <white>" + waystone.founderName()),
                Text.mm("<gray>Maintainer: <white>" + waystone.maintainerName()),
                Text.mm("<gray>State: <white>" + plugin.waystones().effectiveState(waystone, plugin.now())),
                Text.mm("<gray>Location: <white>" + waystone.x() + ", " + waystone.y() + ", " + waystone.z())));
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        java.util.List<net.kyori.adventure.text.Component> loreC = new java.util.ArrayList<>();
        for (String l : lore) loreC.add(Text.mm(l));
        meta.lore(loreC);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WayHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        var manager = plugin.waystones();
        switch (clicked.getType()) {
            case ENDER_PEARL -> {
                player.closeInventory();
                manager.teleportTo(player, holder.waystoneId());
            }
            case HEART_OF_THE_SEA -> {
                player.closeInventory();
                var ws = manager.byId(holder.waystoneId());
                if (ws != null) {
                    var loc = new org.bukkit.Location(plugin.getServer().getWorld(ws.world()), ws.x(), ws.y(), ws.z());
                    if (loc.getBlock() != null) manager.lifecycleOperation(player, loc.getBlock(), "maintain");
                }
            }
            case IRON_AXE -> {
                player.closeInventory();
                var ws = manager.byId(holder.waystoneId());
                if (ws != null) {
                    var loc = new org.bukkit.Location(plugin.getServer().getWorld(ws.world()), ws.x(), ws.y(), ws.z());
                    manager.lifecycleOperation(player, loc.getBlock(), "overwrite");
                }
            }
            case AMETHYST_SHARD -> {
                player.closeInventory();
                var ws = manager.byId(holder.waystoneId());
                if (ws != null) {
                    var loc = new org.bukkit.Location(plugin.getServer().getWorld(ws.world()), ws.x(), ws.y(), ws.z());
                    manager.lifecycleOperation(player, loc.getBlock(), "revive");
                }
            }
            default -> {
            }
        }
    }

    public static final class WayHolder implements InventoryHolder {
        private final long waystoneId;
        private Inventory inventory;

        public WayHolder(long waystoneId) {
            this.waystoneId = waystoneId;
        }

        public long waystoneId() {
            return waystoneId;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
