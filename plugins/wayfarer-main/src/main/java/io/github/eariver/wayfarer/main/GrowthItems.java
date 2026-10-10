package io.github.eariver.wayfarer.main;

import io.github.eariver.wayfarer.main.domain.EvolutionPlan;
import io.github.eariver.wayfarer.main.domain.GrowthTool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Builds and identifies the physical Growth Pickaxe item. */
public final class GrowthItems {

    public static final String PDC_TOOL = "growth_tool_id";
    public static final String PDC_EPOCH = "growth_epoch";
    public static final String PDC_OWNER = "growth_owner";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private GrowthItems() {
    }

    public static Material materialFor(int tier) {
        return switch (tier) {
            case 0 -> Material.STONE_PICKAXE;
            case 1 -> Material.IRON_PICKAXE;
            case 2 -> Material.DIAMOND_PICKAXE;
            default -> Material.NETHERITE_PICKAXE;
        };
    }

    public static String tierName(int tier) {
        return switch (tier) {
            case 0 -> "Stone";
            case 1 -> "Iron";
            case 2 -> "Diamond";
            default -> "Netherite" + (tier > 3 ? " +" + (tier - 3) : "");
        };
    }

    /** Build the physical item for the tool's current tier and branch. */
    public static ItemStack build(WayfarerMainPlugin plugin, GrowthTool tool, int tier) {
        ItemStack item = new ItemStack(materialFor(tier));
        ItemMeta meta = item.getItemMeta();
        EvolutionPlan plan = plugin.plan();
        int evolutions = tier;
        for (int n = 1; n <= evolutions; n++) {
            EvolutionPlan.EnchantmentStep step = plan.enchantmentFor(n, tool.branch());
            Enchantment enchantment = switch (step.kind()) {
                case EFFICIENCY -> Enchantment.EFFICIENCY;
                case UNBREAKING -> Enchantment.UNBREAKING;
                case FORTUNE -> Enchantment.FORTUNE;
                case SILK_TOUCH -> Enchantment.SILK_TOUCH;
            };
            if (enchantment != null) {
                meta.addEnchant(enchantment, step.level(), true);
            }
        }
        meta.getPersistentDataContainer().set(plugin.key(PDC_TOOL), PersistentDataType.STRING, tool.toolId().toString());
        meta.getPersistentDataContainer().set(plugin.key(PDC_EPOCH), PersistentDataType.INTEGER, tool.epoch());
        meta.getPersistentDataContainer().set(plugin.key(PDC_OWNER), PersistentDataType.STRING, tool.ownerUuid().toString());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        applyDisplay(plugin, item, tool, tier);
        return item;
    }

    /** Refresh name/lore/progress display on an existing physical item. */
    public static void applyDisplay(WayfarerMainPlugin plugin, ItemStack item, GrowthTool tool, int tier) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        EvolutionPlan plan = plugin.plan();
        long next = plan.thresholdFor(tier + 1);
        double fraction = plan.fractionToNext(tier, tool.progress());
        int bar = (int) Math.round(fraction * 20);
        String barText = "|".repeat(bar) + "·".repeat(20 - bar);
        Component name = MM.deserialize("<gold><tier> Pickaxe of Growth</gold>",
                Placeholder.unparsed("tier", tierName(tier)));
        meta.displayName(name);
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Growth <white>Tier <tier></white> — Evolution <evo>",
                Placeholder.unparsed("tier", tierName(tier)),
                Placeholder.unparsed("evo", String.valueOf(tier))));
        lore.add(MM.deserialize("<gray>Progress <white><progress></white>/<next>",
                Placeholder.unparsed("progress", String.valueOf(tool.progress())),
                Placeholder.unparsed("next", String.valueOf(next))));
        lore.add(MM.deserialize("<green><bar>", Placeholder.unparsed("bar", barText)));
        lore.add(MM.deserialize("<gray>Branch <white><branch></white>",
                Placeholder.unparsed("branch", tool.branch() == EvolutionPlan.Branch.FORTUNE ? "Fortune" : "Silk Touch")));
        if (tool.status() == GrowthTool.Status.BROKEN) {
            lore.add(MM.deserialize("<red>Broken — repair with /wayfarer-main repair"));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /** Return the tool this item belongs to if it is a Growth Pickaxe of the given owner. */
    public static GrowthTool.Identity identify(WayfarerMainPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String toolId = meta.getPersistentDataContainer().get(plugin.key(PDC_TOOL), PersistentDataType.STRING);
        Integer epoch = meta.getPersistentDataContainer().get(plugin.key(PDC_EPOCH), PersistentDataType.INTEGER);
        String owner = meta.getPersistentDataContainer().get(plugin.key(PDC_OWNER), PersistentDataType.STRING);
        if (toolId == null || epoch == null || owner == null) return null;
        return new GrowthTool.Identity(toolId, epoch, owner);
    }

    public static boolean isPickaxe(ItemStack item) {
        if (item == null) return false;
        return switch (item.getType()) {
            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE -> true;
            default -> false;
        };
    }
}
