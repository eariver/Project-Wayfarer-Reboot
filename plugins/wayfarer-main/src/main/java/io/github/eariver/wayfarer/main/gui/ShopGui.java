package io.github.eariver.wayfarer.main.gui;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.main.WayfarerMainPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
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
import java.util.List;
import java.util.Map;

/**
 * Fixed-price Waymark shop (PoC substitute for EconomyShopGUI — see docs/DECISIONS.md
 * D-2026-10-11-04). Prices follow the reviewed 1 WM minimum / baseline relationship.
 */
public final class ShopGui implements Listener {

    /** Sell prices (what the shop pays the player). */
    public static final Map<Material, Long> SELL = new LinkedHashMap<>(Map.of(
            Material.COBBLESTONE, 1L,
            Material.STONE, 1L,
            Material.COAL, 4L,
            Material.IRON_INGOT, 8L,
            Material.GOLD_INGOT, 10L,
            Material.DIAMOND, 40L,
            Material.EMERALD, 30L,
            Material.ANCIENT_DEBRIS, 200L));

    /** Buy prices. */
    public static final Map<Material, Long> BUY = new LinkedHashMap<>(Map.of(
            Material.BREAD, 2L,
            Material.TORCH, 1L,
            Material.ENDER_PEARL, 30L,
            Material.FIREWORK_ROCKET, 200L,
            Material.GOLDEN_APPLE, 60L,
            Material.EXPERIENCE_BOTTLE, 25L));

    private final WayfarerMainPlugin plugin;

    public ShopGui(WayfarerMainPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        InventoryHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>Waymark Shop"));
        ((ShopHolder) holder).setInventory(inv);
        int slot = 0;
        for (Map.Entry<Material, Long> e : SELL.entrySet()) {
            inv.setItem(slot++, icon(e.getKey(), "<green>Sell", "Sell " + e.getValue() + " WM each", e.getValue()));
        }
        slot = 9;
        for (Map.Entry<Material, Long> e : BUY.entrySet()) {
            inv.setItem(slot++, icon(e.getKey(), "<aqua>Buy", "Buy " + e.getValue() + " WM each", e.getValue()));
        }
        player.openInventory(inv);
    }

    private ItemStack icon(Material material, String name, String lore, long price) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<white>" + pretty(material) + "</white> <gray>(" + name + ")</gray>"));
        meta.lore(List.of(Text.mm("<yellow>" + lore)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        Material material = clicked.getType();
        boolean isSell = SELL.containsKey(material);
        if (!isSell && !BUY.containsKey(material)) return;
        Economy economy = plugin.economy();
        if (economy == null) {
            player.sendMessage(Text.mm("<red>Economy unavailable."));
            return;
        }
        if (isSell) {
            sell(player, economy, material, SELL.get(material));
        } else {
            buy(player, economy, material, BUY.get(material));
        }
    }

    private void sell(Player player, Economy economy, Material material, long price) {
        ItemStack stack = new ItemStack(material, 1);
        if (!player.getInventory().containsAtLeast(stack, 1)) {
            player.sendMessage(Text.mm("<red>You have nothing to sell."));
            return;
        }
        player.getInventory().removeItem(stack);
        economy.depositPlayer(player, price);
        player.sendMessage(Text.mm("<green>Sold <item> for <price> WM.", "item", pretty(material), "price", String.valueOf(price)));
    }

    private void buy(Player player, Economy economy, Material material, long price) {
        if (!economy.has(player, price)) {
            player.sendMessage(Text.mm("<red>You need <price> WM.", "price", String.valueOf(price)));
            return;
        }
        EconomyResponse response = economy.withdrawPlayer(player, price);
        if (!response.transactionSuccess()) {
            player.sendMessage(Text.mm("<red>Purchase failed."));
            return;
        }
        var leftover = player.getInventory().addItem(new ItemStack(material, 1));
        if (!leftover.isEmpty()) {
            economy.depositPlayer(player, price); // refund: no inventory space
            player.sendMessage(Text.mm("<red>No inventory space; refunded."));
            return;
        }
        player.sendMessage(Text.mm("<green>Bought <item> for <price> WM.", "item", pretty(material), "price", String.valueOf(price)));
    }

    public static String pretty(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
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
