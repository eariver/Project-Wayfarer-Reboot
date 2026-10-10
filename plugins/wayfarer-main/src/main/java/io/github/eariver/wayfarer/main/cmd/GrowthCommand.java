package io.github.eariver.wayfarer.main.cmd;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.main.GrowthItems;
import io.github.eariver.wayfarer.main.WayfarerMainPlugin;
import io.github.eariver.wayfarer.main.domain.EvolutionPlan;
import io.github.eariver.wayfarer.main.domain.GrowthTool;
import io.github.eariver.wayfarer.main.domain.RepairPricing;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** /wayfarer-main command tree (alias handled by plugin.yml). */
public final class GrowthCommand implements CommandExecutor {

    private final WayfarerMainPlugin plugin;

    public GrowthCommand(WayfarerMainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("status")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.mm("<red>Players only."));
                return true;
            }
            plugin.growthGui().open(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("repair")) return repair(sender);
        if (sub.equals("reissue")) return reissue(sender, args);
        if (sub.equals("branch")) return branch(sender, args);
        if (sub.equals("admin")) return admin(sender, args);
        sender.sendMessage(Text.mm("<gold>Usage: /wayfarer-main [status|repair|reissue confirm|branch <fortune|silk>]"));
        return true;
    }

    private boolean repair(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.mm("<red>Players only."));
            return true;
        }
        GrowthTool tool = plugin.repository().findActive(player.getUniqueId()).orElse(null);
        if (tool == null) {
            sender.sendMessage(Text.mm("<red>You have no Growth Pickaxe."));
            return true;
        }
        int tier = plugin.tierFor(tool.progress());
        RepairPricing pricing = plugin.pricing();
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean isOurs = GrowthItems.identify(plugin, held) != null;
        int damage = isOurs ? held.getDurability() : 0;
        int max = isOurs ? held.getType().getMaxDurability() : 1;
        boolean broken = tool.status() == GrowthTool.Status.BROKEN;
        long cost = broken ? pricing.brokenRepairCost(tier) : pricing.partialRepairCost(tier, damage, max);
        if (!isOurs && !broken) {
            sender.sendMessage(Text.mm("<red>Hold your Growth Pickaxe to repair it."));
            return true;
        }
        Economy economy = plugin.economy();
        if (cost > 0 && economy != null && !economy.has(player, cost)) {
            sender.sendMessage(Text.mm("<red>Repair costs <white><cost></white> WM; you cannot afford it.", "cost", String.valueOf(cost)));
            return true;
        }
        if (cost > 0 && economy != null) {
            economy.withdrawPlayer(player, cost);
        }
        GrowthTool repaired = tool.withStatus(GrowthTool.Status.ACTIVE);
        plugin.repository().update(repaired);
        if (isOurs) {
            held.setDurability((short) 0);
            GrowthItems.applyDisplay(plugin, held, repaired, tier);
        }
        sender.sendMessage(Text.mm("<green>Repaired for <white><cost></white> WM.", "cost", String.valueOf(cost)));
        return true;
    }

    private boolean reissue(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.mm("<red>Players only."));
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage(Text.mm("<gold>Confirm with /wayfarer-main reissue confirm"));
            return true;
        }
        GrowthTool tool = plugin.repository().findActive(player.getUniqueId()).orElse(null);
        if (tool == null) {
            sender.sendMessage(Text.mm("<red>You have no Growth Pickaxe."));
            return true;
        }
        int tier = plugin.tierFor(tool.progress());
        RepairPricing pricing = plugin.pricing();
        boolean broken = tool.status() == GrowthTool.Status.BROKEN;
        long cost = pricing.reissueCost(tier, broken);
        Economy economy = plugin.economy();
        if (economy != null && !economy.has(player, cost)) {
            sender.sendMessage(Text.mm("<red>Reissue costs <white><cost></white> WM; you cannot afford it.", "cost", String.valueOf(cost)));
            return true;
        }
        if (economy != null) economy.withdrawPlayer(player, cost);
        GrowthTool revoked = tool.withStatus(GrowthTool.Status.REVOKED);
        plugin.repository().update(revoked);
        GrowthTool fresh = new GrowthTool(tool.toolId(), tool.ownerUuid(), player.getName(),
                tool.epoch() + 1, tool.progress(), tool.branch(), GrowthTool.Status.ACTIVE);
        plugin.repository().insert(fresh);
        // remove stale copies still in inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            GrowthTool.Identity id = GrowthItems.identify(plugin, item);
            if (id != null && id.toolId().equals(tool.toolId().toString()) && id.epoch() == tool.epoch()) {
                player.getInventory().setItem(i, null);
            }
        }
        plugin.giveCurrentItem(player, fresh);
        sender.sendMessage(Text.mm("<green>Reissued for <white><cost></white> WM (epoch <epoch>).",
                "cost", String.valueOf(cost), "epoch", String.valueOf(fresh.epoch())));
        return true;
    }

    private boolean branch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.mm("<red>Players only."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.mm("<gold>Usage: /wayfarer-main branch <fortune|silk>"));
            return true;
        }
        EvolutionPlan.Branch branch;
        try {
            branch = EvolutionPlan.Branch.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Text.mm("<red>Unknown branch. Use fortune or silk."));
            return true;
        }
        GrowthTool tool = plugin.repository().findActive(player.getUniqueId()).orElse(null);
        if (tool == null) {
            sender.sendMessage(Text.mm("<red>You have no Growth Pickaxe."));
            return true;
        }
        if (tool.branch() == branch) {
            sender.sendMessage(Text.mm("<gray>Branch already <branch>.", "branch", branch.name()));
            return true;
        }
        GrowthTool updated = tool.withBranch(branch);
        plugin.repository().update(updated);
        // rebuild the physical item so enchantments reflect the branch
        int tier = plugin.tierFor(updated.progress());
        player.getInventory().setItemInMainHand(GrowthItems.build(plugin, updated, tier));
        sender.sendMessage(Text.mm("<green>Branch set to <branch>.", "branch", branch.name()));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wayfarer.main.admin")) {
            sender.sendMessage(Text.mm("<red>You may not do that."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.mm("<gold>Usage: /wayfarer-main admin <grant|setprogress|revoke> <player> [args]"));
            return true;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
        if (target == null) {
            sender.sendMessage(Text.mm("<red>Player must be online."));
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "grant" -> plugin.ensureFirstTool(target);
            case "setprogress" -> {
                if (args.length < 4) {
                    sender.sendMessage(Text.mm("<red>Usage: /wayfarer-main admin setprogress <player> <amount>"));
                    return true;
                }
                long amount = Long.parseLong(args[3]);
                GrowthTool tool = plugin.repository().findActive(target.getUniqueId()).orElse(null);
                if (tool == null) {
                    sender.sendMessage(Text.mm("<red>Target has no tool."));
                    return true;
                }
                GrowthTool updated = tool.withProgress(amount);
                plugin.repository().update(updated);
                int tier = plugin.tierFor(amount);
                target.getInventory().setItemInMainHand(GrowthItems.build(plugin, updated, tier));
                sender.sendMessage(Text.mm("<green>Progress set for <name>.", "name", target.getName()));
            }
            case "revoke" -> {
                GrowthTool tool = plugin.repository().findActive(target.getUniqueId()).orElse(null);
                if (tool == null) return true;
                plugin.repository().update(tool.withStatus(GrowthTool.Status.REVOKED));
                sender.sendMessage(Text.mm("<green>Revoked <name>'s pickaxe.", "name", target.getName()));
            }
            default -> sender.sendMessage(Text.mm("<red>Unknown admin subcommand."));
        }
        return true;
    }
}
