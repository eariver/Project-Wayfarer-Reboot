package io.github.eariver.wayfarer.main.gui;

import io.github.eariver.wayfarer.common.GuiClicks;
import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.main.GrowthItems;
import io.github.eariver.wayfarer.main.WayfarerMainPlugin;
import io.github.eariver.wayfarer.main.domain.GrowthTool;
import io.github.eariver.wayfarer.main.domain.RepairPricing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Growth Pickaxe status GUI (right-click the pickaxe or /wayfarer-main). */
public final class GrowthGui implements Listener {

    private static final int SLOT_HEAD = 4;
    private static final int SLOT_REPAIR = 11;
    private static final int SLOT_REISSUE = 15;

    private final WayfarerMainPlugin plugin;

    public GrowthGui(WayfarerMainPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        // R-09: the live view (buffered progress included) is the single source of truth
        GrowthTool tool = plugin.viewOf(player.getUniqueId());
        if (tool == null) {
            player.sendMessage(Text.mm("<red>No Growth Pickaxe found."));
            return;
        }
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Text.mm("<dark_gray>Pickaxe of Growth"));
        holder.setInventory(inv);

        int tier = plugin.tierFor(tool.progress());
        RepairPricing pricing = plugin.pricing();
        // R-07: use the item's source of truth for the material/name so the GUI, the
        // physical item and /wayfarer-main status cannot disagree (a Diamond tool that
        // has not bought the Netherite upgrade stays a Diamond pickaxe).
        ItemStack head = new ItemStack(GrowthItems.materialFor(tier, tool.netheriteUpgraded()));
        ItemMeta meta = head.getItemMeta();
        meta.displayName(Text.mm("<gold>Evolution <tier>", "tier", GrowthItems.tierName(tier, tool.netheriteUpgraded())));
        meta.lore(List.of(
                Text.mm("<gray>Tier: <white><name>",
                        "name", GrowthItems.tierName(tier, tool.netheriteUpgraded())),
                Text.mm("<gray>Progress: <white><progress></white>",
                        "progress", String.valueOf(tool.progress())),
                Text.mm("<gray>Next evolution: <white><next></white>",
                        "next", String.valueOf(plugin.plan().thresholdFor(tier + 1))),
                Text.mm("<gray>Branch: <white><branch></white>", "branch", tool.branch().name()),
                Text.mm("<gray>Status: <white><status></white>", "status", tool.status().name())
        ));
        head.setItemMeta(meta);
        inv.setItem(SLOT_HEAD, head);

        inv.setItem(SLOT_REPAIR, button(Material.ANVIL, "<yellow>Repair",
                "<gray>Restore full durability for <white><cost></white> WM", "cost",
                String.valueOf(pricing.fullRepairCost(tier))));
        inv.setItem(SLOT_REISSUE, button(Material.CRAFTING_TABLE, "<yellow>Reissue",
                "<gray>Replace a lost pickaxe for <white><cost></white> WM", "cost",
                String.valueOf(pricing.reissueCost(tier, tool.status() == GrowthTool.Status.BROKEN))));
        player.openInventory(inv);
    }

    /**
     * R-14: only clicks on OUR top inventory count. {@code getCurrentItem()} alone also
     * reports the item under the cursor when the player clicks their own inventory, so a
     * diamond in the player's bag must not trigger a purchase. Every other action
     * (shift click, number key, drag, double click, collect-to-cursor) is cancelled.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());
        if (!GuiClicks.countsAsButtonClick(clickedTop, event.getRawSlot(),
                event.getView().getTopInventory().getSize())) {
            // click in the player's own inventory (or outside): block item movement
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        // only the real buttons react: matching the material alone is not enough
        if (slot == SLOT_REPAIR && clicked.getType() == Material.ANVIL) {
            player.closeInventory();
            player.performCommand("wayfarer-main repair");
        } else if (slot == SLOT_REISSUE && clicked.getType() == Material.CRAFTING_TABLE) {
            player.closeInventory();
            player.performCommand("wayfarer-main reissue confirm");
        }
    }

    /** Drags across the GUI must never move items in or out. */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        boolean touchesTop = event.getRawSlots().stream()
                .anyMatch(s -> s < event.getView().getTopInventory().getSize());
        if (touchesTop || event.getInventory().equals(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    private ItemStack button(Material material, String name, String loreLine, String ph, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Text.mm(loreLine, ph, value).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    /** Marker holder so we can identify our GUI in the click event. */
    public static final class Holder implements InventoryHolder {
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
