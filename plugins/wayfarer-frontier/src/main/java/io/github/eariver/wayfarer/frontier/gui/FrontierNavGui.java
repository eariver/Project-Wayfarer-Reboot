package io.github.eariver.wayfarer.frontier.gui;

import io.github.eariver.wayfarer.common.GuiClicks;
import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.frontier.WayfarerFrontierPlugin;
import io.github.eariver.wayfarer.frontier.item.ThemeItems.ItemType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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

    /**
     * C1/P-2: the navigation item is stamped on every path so identify() works and
     * right-click opens this GUI. Unstamped legacy compasses from earlier builds are
     * removed on join, so players never accumulate duplicates.
     */
    public void ensureNavigationItem(Player player) {
        if (plugin.themeItems().findInInventory(player, ItemType.NAVIGATION) != null) return;
        removeLegacyCompasses(player);
        var items = plugin.themeItems();
        ItemStack tool = items.build(ItemType.NAVIGATION, player.getUniqueId(), "frontier", 0);
        items.stamp(tool, ItemType.NAVIGATION, player.getUniqueId(), "frontier", 0);
        var left = player.getInventory().addItem(tool);
        left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }

    /** Remove unstamped "Wayfarer Navigation" compasses minted by earlier builds (P-2 migration). */
    private void removeLegacyCompasses(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != Material.COMPASS) continue;
            if (plugin.themeItems().identify(item) != null) continue;
            ItemMeta meta = item.getItemMeta();
            String name = meta != null && meta.hasDisplayName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(meta.displayName())
                    : "";
            if (name.contains("Wayfarer Navigation")) {
                player.getInventory().setItem(i, null);
            }
        }
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

    /**
     * R-14: only clicks on our own top inventory count. getCurrentItem() also reports
     * the item under the cursor when the player clicks their own inventory, so an elytra
     * in the player's bag must not trigger navigation. Shift/number-key/drag actions are
     * cancelled wholesale.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NavHolder)) return;
        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());
        if (!GuiClicks.countsAsButtonClick(clickedTop, event.getRawSlot(),
                event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        // resolve the action from the slot's real button, never from the material alone
        NavAction action = NavAction.forSlot(slot, clicked.getType());
        if (action == null) return;
        player.closeInventory();
        action.run(plugin, player);
    }

    /** Drags across the GUI must never move items in or out. */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof NavHolder)) return;
        boolean touchesTop = event.getRawSlots().stream()
                .anyMatch(s -> s < event.getView().getTopInventory().getSize());
        if (touchesTop) event.setCancelled(true);
    }

    private enum NavAction {
        WORLDS_BEYOND(10, Material.ELYTRA) {
            @Override
            void run(WayfarerFrontierPlugin plugin, Player player) {
                if (plugin.safeTeleport(player, "worlds-beyond")) {
                    plugin.themeItems().grantLoadoutOnEntry(player);
                } else {
                    player.sendMessage(Text.mm("<red>Worlds Beyond arrival is not available."));
                }
            }
        },
        RUINED_FRONTIER(12, Material.NETHERITE_SWORD) {
            @Override
            void run(WayfarerFrontierPlugin plugin, Player player) {
                if (plugin.safeTeleport(player, "ruined-frontier")) {
                    plugin.themeItems().grantLoadoutOnEntry(player);
                } else {
                    player.sendMessage(Text.mm("<red>Ruined Frontier arrival is not available."));
                }
            }
        },
        SHOP(14, Material.EMERALD) {
            @Override
            void run(WayfarerFrontierPlugin plugin, Player player) {
                plugin.shopGui().open(player);
            }
        },
        RETURN_TO_GATE(16, Material.RECOVERY_COMPASS) {
            @Override
            void run(WayfarerFrontierPlugin plugin, Player player) {
                Location gate = plugin.gateArrival();
                if (gate == null || gate.getWorld() == null) {
                    player.sendMessage(Text.mm("<red>The gate is not available."));
                    return;
                }
                if (!player.teleport(gate)) {
                    player.sendMessage(Text.mm("<red>Teleport to the gate failed."));
                }
            }
        };

        private final int slot;
        private final Material material;

        NavAction(int slot, Material material) {
            this.slot = slot;
            this.material = material;
        }

        static NavAction forSlot(int slot, Material material) {
            for (NavAction action : values()) {
                if (action.slot == slot && action.material == material) return action;
            }
            return null;
        }

        abstract void run(WayfarerFrontierPlugin plugin, Player player);
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
