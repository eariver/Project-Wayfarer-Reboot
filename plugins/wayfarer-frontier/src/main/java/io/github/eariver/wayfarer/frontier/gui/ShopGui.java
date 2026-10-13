package io.github.eariver.wayfarer.frontier.gui;

import io.github.eariver.wayfarer.common.GuiClicks;
import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.frontier.WayfarerFrontierPlugin;
import io.github.eariver.wayfarer.frontier.item.ThemeItems;
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

/** Frontier Waymark shop: launchpads, flight rockets, waystone tools. */
public final class ShopGui implements Listener {

    public static final Map<Material, Long> OFFERS = new LinkedHashMap<>(Map.of(
            Material.LIGHT_WEIGHTED_PRESSURE_PLATE, 30L,
            Material.FIREWORK_ROCKET, 200L,
            Material.AMETHYST_SHARD, 600L));
    /** Offer icons in slot order (slot 10..). */
    public static final List<Material> OFFER_ORDER = List.copyOf(OFFERS.keySet());

    private final WayfarerFrontierPlugin plugin;

    public ShopGui(WayfarerFrontierPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * C4/P-5: WB-only shop. Gated at open time AND re-checked at click time so world
     * switches or GUI races cannot move theme items across the MVI groups (RF allowance
     * for launchpads is an open design call — see docs/DECISIONS.md).
     */
    public void open(Player player) {
        if (!plugin.worldsBeyond().contains(player.getWorld().getName())) {
            player.sendMessage(Text.mm("<red>The Frontier shop is only available in Worlds Beyond."));
            return;
        }
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>Frontier Waymark Shop"));
        holder.setInventory(inv);
        int slot = 10;
        for (Material material : OFFER_ORDER) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.mm("<white>" + pretty(material)));
            meta.lore(java.util.List.of(Text.mm("<yellow>Buy for <price> WM",
                    "price", String.valueOf(OFFERS.get(material)))));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }

    /**
     * R-14: only clicks on our own top inventory count, and the action is resolved from
     * the slot's real button — so a pressure plate or a rocket in the player's own
     * inventory can never trigger a purchase. R-15: the refund leg must be confirmed
     * before the player is told they were refunded.
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
        // revalidate the theme at purchase time: world/group must still be Worlds Beyond
        if (!plugin.worldsBeyond().contains(player.getWorld().getName())) {
            player.closeInventory();
            player.sendMessage(Text.mm("<red>Purchase cancelled: you left Worlds Beyond."));
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        Long price = priceAt(slot, clicked.getType());
        if (price == null) return;
        var economy = plugin.getServer().getServicesManager()
                .getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economy == null || economy.getProvider() == null) {
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
        if (clicked.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            type = ThemeItems.ItemType.LAUNCHPAD;
            give = items.build(type, player.getUniqueId(), "worlds_beyond", 0);
        } else if (clicked.getType() == Material.FIREWORK_ROCKET) {
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
            boolean refunded = compensate(provider, player, price);
            player.sendMessage(refunded
                    ? Text.mm("<red>No inventory space; refunded.")
                    : Text.mm("<red>No inventory space and the refund FAILED. Reference: frontier-shop-refund"));
            return;
        }
        player.sendMessage(Text.mm("<green>Bought <item> for <price> WM.",
                "item", pretty(clicked.getType()), "price", String.valueOf(price)));
    }

    /** Price of the offer that really sits in this slot (not merely that material). */
    private Long priceAt(int slot, Material material) {
        int index = slot - 10;
        if (index < 0 || index >= OFFER_ORDER.size()) return null;
        return OFFER_ORDER.get(index) == material ? OFFERS.get(material) : null;
    }

    /** Drags across the shop must never move items in or out. */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        boolean touchesTop = event.getRawSlots().stream()
                .anyMatch(s -> s < event.getView().getTopInventory().getSize());
        if (touchesTop) event.setCancelled(true);
    }

    /** Confirmed compensating credit. */
    private static boolean compensate(net.milkbowl.vault.economy.Economy provider, Player player, long amount) {
        try {
            var response = provider.depositPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (RuntimeException e) {
            return false;
        }
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
