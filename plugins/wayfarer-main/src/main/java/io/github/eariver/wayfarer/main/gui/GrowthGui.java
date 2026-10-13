package io.github.eariver.wayfarer.main.gui;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.main.WayfarerMainPlugin;
import io.github.eariver.wayfarer.main.domain.GrowthTool;
import io.github.eariver.wayfarer.main.domain.RepairPricing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

/** Growth Pickaxe status GUI (right-click the pickaxe or /wayfarer-main). */
public final class GrowthGui implements Listener {

    private final WayfarerMainPlugin plugin;

    public GrowthGui(WayfarerMainPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        GrowthTool tool = plugin.repository().findActive(player.getUniqueId()).orElse(null);
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
        ItemStack head = new ItemStack(switch (tier) {
            case 0 -> Material.STONE_PICKAXE;
            case 1 -> Material.IRON_PICKAXE;
            case 2 -> Material.DIAMOND_PICKAXE;
            default -> Material.NETHERITE_PICKAXE;
        });
        ItemMeta meta = head.getItemMeta();
        meta.displayName(Text.mm("<gold>Evolution <tier>", "tier", String.valueOf(tier)));
        meta.lore(List.of(
                Text.mm("<gray>Tier: <white><name>", "name", io.github.eariver.wayfarer.main.GrowthItems.tierName(tier, false)),
                Text.mm("<gray>Progress: <white><progress></white>", "progress", String.valueOf(tool.progress())),
                Text.mm("<gray>Next evolution: <white><next></white>", "next", String.valueOf(plugin.plan().thresholdFor(tier + 1))),
                Text.mm("<gray>Branch: <white><branch></white>", "branch", tool.branch().name())
        ));
        head.setItemMeta(meta);
        inv.setItem(4, head);

        ItemStack repair = button(Material.ANVIL, "<yellow>Repair",
                "<gray>Restore full durability for <white><cost></white> WM", "cost", String.valueOf(pricing.fullRepairCost(tier)));
        inv.setItem(11, repair);
        ItemStack reissue = button(Material.CRAFTING_TABLE, "<yellow>Reissue",
                "<gray>Replace a lost pickaxe for <white><cost></white> WM", "cost",
                String.valueOf(pricing.reissueCost(tier, tool.status() == GrowthTool.Status.BROKEN)));
        inv.setItem(15, reissue);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        if (clicked.getType() == Material.ANVIL) {
            player.closeInventory();
            player.performCommand("wayfarer-main repair");
        } else if (clicked.getType() == Material.CRAFTING_TABLE) {
            player.closeInventory();
            player.performCommand("wayfarer-main reissue confirm");
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
