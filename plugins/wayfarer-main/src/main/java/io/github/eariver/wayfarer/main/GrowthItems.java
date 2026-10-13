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

/** Builds and identifies the physical Growth Pickaxe item (incl. the BROKEN gray-dye form). */
public final class GrowthItems {

    public static final String PDC_TOOL = "growth_tool_id";
    public static final String PDC_EPOCH = "growth_epoch";
    public static final String PDC_OWNER = "growth_owner";
    public static final String BROKEN_NAME = "壊れた成長のピッケル";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private GrowthItems() {
    }

    /** Effective material for the tool's tier (netherite is a persistent upgrade, not a tier). */
    public static Material materialFor(int tier, boolean netheriteUpgraded) {
        if (EvolutionPlan.netheriteEffective(tier, netheriteUpgraded)) {
            return Material.NETHERITE_PICKAXE;
        }
        return baseMaterial(tier);
    }

    public static Material baseMaterial(int tier) {
        return switch (tier) {
            case 1 -> Material.STONE_PICKAXE;
            case 2 -> Material.IRON_PICKAXE;
            default -> Material.DIAMOND_PICKAXE;
        };
    }

    public static String tierName(int tier, boolean netheriteUpgraded) {
        if (EvolutionPlan.netheriteEffective(tier, netheriteUpgraded)) {
            return "Netherite";
        }
        String base = switch (tier) {
            case 1 -> "Stone";
            case 2 -> "Iron";
            default -> "Diamond";
        };
        int steps = Math.max(0, tier - EvolutionPlan.DIAMOND);
        if (steps > 0) {
            return base + " +" + steps;
        }
        return base;
    }

    /** Build the physical item for the tool's current state, preserving worn durability. */
    public static ItemStack build(WayfarerMainPlugin plugin, GrowthTool tool) {
        int tier = plugin.tierFor(tool.progress());
        if (tool.status() == GrowthTool.Status.BROKEN) {
            return buildBroken(plugin, tool);
        }
        ItemStack item = new ItemStack(materialFor(tier, tool.netheriteUpgraded()));
        ItemMeta meta = item.getItemMeta();
        applyEnchants(plugin, meta, tool);
        stamp(plugin, meta, tool);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        applyDisplay(plugin, item, tool, tier, 0);
        return item;
    }

    private static void applyEnchants(WayfarerMainPlugin plugin, ItemMeta meta, GrowthTool tool) {
        EvolutionPlan plan = plugin.plan();
        int steps = plan.enchantSteps(plugin.tierFor(tool.progress()));
        for (int n = 1; n <= steps; n++) {
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
    }

    private static void stamp(WayfarerMainPlugin plugin, ItemMeta meta, GrowthTool tool) {
        meta.getPersistentDataContainer().set(plugin.key(PDC_TOOL), PersistentDataType.STRING, tool.toolId().toString());
        meta.getPersistentDataContainer().set(plugin.key(PDC_EPOCH), PersistentDataType.INTEGER, tool.epoch());
        meta.getPersistentDataContainer().set(plugin.key(PDC_OWNER), PersistentDataType.STRING, tool.ownerUuid().toString());
    }

    /** BROKEN form: gray dye identity shell (concept v0.0.5 §19), keeps all PDC. */
    public static ItemStack buildBroken(WayfarerMainPlugin plugin, GrowthTool tool) {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        stamp(plugin, meta, tool);
        item.setItemMeta(meta);
        applyDisplay(plugin, item, tool, plugin.tierFor(tool.progress()), 0);
        return item;
    }

    /**
     * Refresh name/lore/durability readout. Never changes damage, enchants or identity —
     * callers pass the worn damage so the display stays truthful.
     */
    public static void applyDisplay(WayfarerMainPlugin plugin, ItemStack item, GrowthTool tool, int tier, int damage) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        short max = item.getType().getMaxDurability();
        boolean broken = tool.status() == GrowthTool.Status.BROKEN;
        if (broken) {
            meta.displayName(MM.deserialize("<gray><name></gray>", Placeholder.unparsed("name", BROKEN_NAME)));
        } else {
            meta.displayName(MM.deserialize("<gold><name> Pickaxe of Growth</gold>",
                    Placeholder.unparsed("name", tierName(tier, tool.netheriteUpgraded()))));
        }
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Tier: <white><name></white>",
                Placeholder.unparsed("name", tierName(tier, tool.netheriteUpgraded()))));
        long next = plugin.plan().thresholdFor(tier + 1);
        String progressLine = tool.progress() >= next
                ? "<gray>Progress: <white><progress></white> (next: <next>)"
                : "<gray>Progress: <white><progress></white>/<next>";
        lore.add(MM.deserialize(progressLine,
                Placeholder.unparsed("progress", String.valueOf(tool.progress())),
                Placeholder.unparsed("next", String.valueOf(next))));
        String bar = progressBar(plugin.plan().fractionToNext(tier, tool.progress()));
        lore.add(MM.deserialize("<green><bar></green>", Placeholder.unparsed("bar", bar)));
        String enchants = enchantSummary(plugin, tool, tier);
        if (!enchants.isEmpty()) {
            lore.add(MM.deserialize("<aqua>Enchants: <white><enchants></white>",
                    Placeholder.unparsed("enchants", enchants)));
        }
        if (broken) {
            lore.add(MM.deserialize("<red>BROKEN — /wayfarer-main repair to restore"));
            lore.add(MM.deserialize("<dark_gray>Epoch <epoch>",
                    Placeholder.unparsed("epoch", String.valueOf(tool.epoch()))));
        } else if (max > 0) {
            String color = damage >= max * 0.8 ? "<red>" : damage >= max * 0.5 ? "<gold>" : "<white>";
            lore.add(MM.deserialize("<gray>Durability: <color><damage></color>/<max>",
                    Placeholder.unparsed("color", color),
                    Placeholder.unparsed("damage", String.valueOf(max - damage)),
                    Placeholder.unparsed("max", String.valueOf(max))));
        }
        lore.add(MM.deserialize("<gray>Branch: <white><branch></white>",
                Placeholder.unparsed("branch", tool.branch() == EvolutionPlan.Branch.FORTUNE ? "Fortune" : "Silk Touch")));
        if (tool.netheriteUpgraded()) {
            lore.add(MM.deserialize("<light_purple>Netherite Upgrade: <white>unlocked</white>"));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    public static String progressBar(double fraction) {
        int bar = (int) Math.round(fraction * 20);
        return "|".repeat(bar) + "·".repeat(20 - bar);
    }

    public static String enchantSummary(WayfarerMainPlugin plugin, GrowthTool tool, int tier) {
        if (tool.status() == GrowthTool.Status.BROKEN) return "";
        EvolutionPlan plan = plugin.plan();
        int steps = plan.enchantSteps(tier);
        if (steps == 0) return "none";
        int eff = 0;
        int unb = 0;
        int fortune = 0;
        boolean silk = false;
        for (int n = 1; n <= steps; n++) {
            var step = plan.enchantmentFor(n, tool.branch());
            switch (step.kind()) {
                case EFFICIENCY -> eff = step.level();
                case UNBREAKING -> unb = step.level();
                case FORTUNE -> fortune = step.level();
                case SILK_TOUCH -> silk = true;
            }
        }
        List<String> parts = new ArrayList<>();
        if (eff > 0) parts.add("Efficiency " + eff);
        if (unb > 0) parts.add("Unbreaking " + unb);
        if (fortune > 0) parts.add("Fortune " + fortune);
        if (silk) parts.add("Silk Touch");
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    /** Return the identity stamped on the item, or null when it is not a Growth tool. */
    public static GrowthTool.Identity identify(WayfarerMainPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String toolId = pdc.get(plugin.key(PDC_TOOL), PersistentDataType.STRING);
        Integer epoch = pdc.get(plugin.key(PDC_EPOCH), PersistentDataType.INTEGER);
        String owner = pdc.get(plugin.key(PDC_OWNER), PersistentDataType.STRING);
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
