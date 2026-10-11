package io.github.eariver.wayfarer.frontier.gui;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.frontier.WayfarerFrontierPlugin;
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

/** Navigation GUI opened by the Wayfarer Navigation item or /wayfarer-frontier open. */
public final class FrontierNavGui implements Listener {

    private final WayfarerFrontierPlugin plugin;

    public FrontierNavGui(WayfarerFrontierPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void ensureNavigationItem(Player player) {
        if (plugin.themeItems().findInInventory(player) != null) return;
        var tool = plugin.themeItems().build(
                io.github.eariver.wayfarer.frontier.item.ThemeItems.ItemType.NAVIGATION,
                player.getUniqueId(), "frontier", 0);
        var left = player.getInventory().addItem(tool);
        left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }

    public void open(Player player) {
        NavHolder holder = new NavHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm("<dark_purple>Frontier Navigation"));
        holder.setInventory(inv);
        inv.setItem(10, button(Material.ELYTRA, "<aqua>Worlds Beyond",
                List.of("<gray>Glide, grapple and waystone across frontier_iris")));
        inv.setItem(12, button(Material.NETHERITE_SWORD, "<red>Ruined Frontier",
                List.of("<gray>Dive into the BetterStructures ruins and fight elites")));
        inv.setItem(14, button(Material.EMERALD, "<gold>Waymark Shop",
                List.of("<gray>Launchpads, rockets, waystone tools")));
        inv.setItem(16, button(Material.RECOVERY_COMPASS, "<gray>Return to Gate",
                List.of("<gray>Back to the Frontier gate hub")));
        player.openInventory(inv);
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
        if (!(event.getInventory().getHolder() instanceof NavHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        player.closeInventory();
        switch (clicked.getType()) {
            case ELYTRA -> {
                var arrival = plugin.arrival("worlds-beyond");
                if (arrival != null) {
                    player.teleport(arrival);
                    plugin.themeItems().grantLoadoutOnEntry(player);
                } else {
                    player.sendMessage(Text.mm("<red>Worlds Beyond arrival is not configured."));
                }
            }
            case NETHERITE_SWORD -> {
                var arrival = plugin.arrival("ruined-frontier");
                if (arrival != null) player.teleport(arrival);
                else player.sendMessage(Text.mm("<red>Ruined Frontier arrival is not configured."));
            }
            case EMERALD -> plugin.shopGui().open(player);
            case RECOVERY_COMPASS -> player.teleport(plugin.gateArrival());
            default -> {
            }
        }
    }

    public static final class NavHolder implements InventoryHolder {
        private Inventory inventory;

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
