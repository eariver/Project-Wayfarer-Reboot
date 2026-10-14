package io.github.eariver.wayfarer.main;

import io.github.eariver.wayfarer.main.domain.GrowthTool;
import org.bukkit.inventory.ItemStack;

/** Item helpers shared by the main plugin. */
public final class Items {
    private Items() {
    }

    /** Rebuild a tool item while preserving its current worn damage (no free durability). */
    public static ItemStack rebuildPreservingDamage(WayfarerMainPlugin plugin, GrowthTool tool, ItemStack current) {
        short damage = current == null ? 0 : current.getDurability();
        ItemStack rebuilt = GrowthItems.build(plugin, tool);
        if (damage > 0 && rebuilt.getType().getMaxDurability() >= damage) {
            rebuilt.setDurability(damage);
            GrowthItems.applyDisplay(plugin, rebuilt, tool, plugin.tierFor(tool.progress()), damage);
        }
        return rebuilt;
    }
}
