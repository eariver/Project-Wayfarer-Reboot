package io.github.eariver.wayfarer.frontier.gui;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.frontier.WayfarerFrontierPlugin;
import io.github.eariver.wayfarer.frontier.item.ThemeItems;
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

import java.util.LinkedHashMap;
import java.util.Map;

/** Frontier Waymark shop: launchpads, flight rockets, waystone tools. */
public final class ShopGui implements Listener {

    public static final Map<Material, Long> OFFERS = new LinkedHashMap<>(Map.of(
            Material.LIGHT_WEIGHTED_PRESSURE_PLATE, 30L,
            Material.FIREWORK_ROCKET, 200L,
            Material.AMETHYST_SHARD, 600L));

    private final WayfarerFrontierPlugin plugin;

    public ShopGui(WayfarerFrontierPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>Frontier Waymark Shop"));
        holder.setInventory(inv);
        int slot = 10;
        for (Map.Entry<Material, Long> e : OFFERS.entrySet()) {
            ItemStack item = new ItemStack(e.getKey());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.mm("<white>" + pretty(e.getKey())));
            meta.lore(java.util.List.of(Text.mm("<yellow>Buy for <price> WM", "price", String.valueOf(e.getValue()))));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        Material material = clicked.getType();
        Long price = OFFERS.get(material);
        if (price == null) return;
        var economy = plugin.getServer().getServicesManager()
                .getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economy == null) {
            player.sendMessage(Text.mm("<red>Economy unavailable."));
            return;
        }
        var provider = economy.getProvider();
        if (!provider.has(player, price)) {
            player.sendMessage(Text.mm("<red>You need <price> WM.", "price", String.valueOf(price)));
            return;
        }
        if (!provider.withdrawPlayer(player, price).transactionSuccess()) {
            player.sendMessage(Text.mm("<red>Purchase failed."));
            return;
        }
        ThemeItems items = plugin.themeItems();
        ItemStack give;
        ThemeItems.ItemType type = null;
        if (material == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            type = ThemeItems.ItemType.LAUNCHPAD;
            give = items.build(type, player.getUniqueId(), "worlds_beyond", 0);
        } else if (material == Material.FIREWORK_ROCKET) {
            give = new ItemStack(Material.FIREWORK_ROCKET, 1);
        } else {
            type = ThemeItems.ItemType.WAYSTONE_TOOL;
            give = items.build(type, player.getUniqueId(), "worlds_beyond", 0);
        }
        if (type != null) {
            items.stamp(give, type, player.getUniqueId(), "worlds_beyond", 0);
        }
        var left = player.getInventory().addItem(give);
        if (!left.isEmpty()) {
            provider.depositPlayer(player, price);
            player.sendMessage(Text.mm("<red>No inventory space; refunded."));
            return;
        }
        player.sendMessage(Text.mm("<green>Bought <item> for <price> WM.", "item", pretty(material), "price", String.valueOf(price)));
    }

    public static String pretty(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.length() > 1 ? p.substring(1) : "").append(' ');
        }
        return sb.toString().trim();
    }

    public static final class ShopHolder implements InventoryHolder {
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
