package io.github.eariver.wayfarer.main.gui;

import io.github.eariver.wayfarer.common.GuiClicks;
import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.main.WayfarerMainPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
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

    /** Sell icons in slot order (slot 0..). */
    public static final List<Material> SELL_ORDER = List.copyOf(SELL.keySet());
    /** Buy icons in slot order (slot 9..). */
    public static final List<Material> BUY_ORDER = List.copyOf(BUY.keySet());

    public void open(Player player) {
        InventoryHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>Waymark Shop"));
        ((ShopHolder) holder).setInventory(inv);
        int slot = 0;
        for (Material m : SELL_ORDER) {
            inv.setItem(slot++, icon(m, "<green>Sell", "Sell " + SELL.get(m) + " WM each", SELL.get(m)));
        }
        slot = 9;
        for (Material m : BUY_ORDER) {
            inv.setItem(slot++, icon(m, "<aqua>Buy", "Buy " + BUY.get(m) + " WM each", BUY.get(m)));
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

    /**
     * R-14: only clicks on our own top inventory count, and the action is resolved from
     * the slot's real button. Without this a diamond in the player's own inventory would
     * be sold just because the shop also offers diamonds.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
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
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        Material material = clicked.getType();
        Long sellPrice = sellPriceAt(event.getRawSlot(), material);
        Long buyPrice = buyPriceAt(event.getRawSlot(), material);
        Economy economy = plugin.economy();
        if (economy == null) {
            player.sendMessage(Text.mm("<red>Economy unavailable."));
            return;
        }
        if (sellPrice != null) {
            sell(player, economy, material, sellPrice);
        } else if (buyPrice != null) {
            buy(player, economy, material, buyPrice);
        }
    }

    /** Price of the sell icon that really sits in this slot (not merely that material). */
    private Long sellPriceAt(int slot, Material material) {
        ItemStack icon = slot >= 0 && slot < SELL_ORDER.size() ? new ItemStack(SELL_ORDER.get(slot)) : null;
        return icon != null && icon.getType() == material ? SELL.get(material) : null;
    }

    private Long buyPriceAt(int slot, Material material) {
        int index = slot - 9;
        if (index < 0 || index >= BUY_ORDER.size()) return null;
        ItemStack icon = new ItemStack(BUY_ORDER.get(index));
        return icon.getType() == material ? BUY.get(material) : null;
    }

    /** Drags across the shop must never move items in or out. */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        boolean touchesTop = event.getRawSlots().stream()
                .anyMatch(s -> s < event.getView().getTopInventory().getSize());
        if (touchesTop) event.setCancelled(true);
    }

    private void sell(Player player, Economy economy, Material material, long price) {
        ItemStack stack = new ItemStack(material, 1);
        if (!player.getInventory().containsAtLeast(stack, 1)) {
            player.sendMessage(Text.mm("<red>You have nothing to sell."));
            return;
        }
        // R-15: credit FIRST, then take the item; if the credit is not confirmed the item
        // stays in the inventory (never a free item for the shop).
        EconomyResponse credit = economy.depositPlayer(player, price);
        if (credit == null || !credit.transactionSuccess()) {
            player.sendMessage(Text.mm("<red>Sale failed; the item was not taken."));
            return;
        }
        ItemStack removed = player.getInventory().removeItem(stack).isEmpty() ? stack : null;
        if (removed == null) {
            // the item could not be removed after the credit: compensate and report
            compensate(economy, player, price);
            player.sendMessage(Text.mm("<red>Inventory changed during the sale; you were not charged or credited."));
            return;
        }
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
            // R-15: the refund leg must be confirmed before the player is told
            boolean refunded = compensate(economy, player, price);
            player.sendMessage(refunded
                    ? Text.mm("<red>No inventory space; refunded.")
                    : Text.mm("<red>No inventory space and the refund FAILED. Reference: waymark-buy-refund"));
            return;
        }
        player.sendMessage(Text.mm("<green>Bought <item> for <price> WM.", "item", pretty(material), "price", String.valueOf(price)));
    }

    /** Confirmed compensating credit; returns true when the provider confirmed it. */
    private static boolean compensate(Economy economy, Player player, long amount) {
        try {
            EconomyResponse response = economy.depositPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (RuntimeException e) {
            return false;
        }
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
