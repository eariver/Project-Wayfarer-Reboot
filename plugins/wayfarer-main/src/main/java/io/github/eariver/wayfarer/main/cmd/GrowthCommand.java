package io.github.eariver.wayfarer.main.cmd;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.main.GrowthItems;
import io.github.eariver.wayfarer.main.Items;
import io.github.eariver.wayfarer.main.WayfarerMainPlugin;
import io.github.eariver.wayfarer.main.domain.EvolutionPlan;
import io.github.eariver.wayfarer.main.domain.GrowthTool;
import io.github.eariver.wayfarer.main.domain.RepairPricing;
import io.github.eariver.wayfarer.main.gui.GrowthGui;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** /wayfarer-main command tree. */
public final class GrowthCommand implements CommandExecutor {

    private final WayfarerMainPlugin plugin;

    public GrowthCommand(WayfarerMainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.mm("<red>Players only."));
                return true;
            }
            plugin.growthGui().open(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("status")) {
            Player player = args.length >= 2 ? Bukkit.getPlayerExact(args[1])
                    : sender instanceof Player p ? p : null;
            if (player == null) {
                sender.sendMessage(Text.mm("<red>Players only (or specify an online player)."));
                return true;
            }
            GrowthTool tool = plugin.repository().findActive(player.getUniqueId()).orElse(null);
            if (tool == null) {
                sender.sendMessage(Text.mm("<red>No Growth Pickaxe for <name>.", "name", player.getName()));
                return true;
            }
            int tier = plugin.tierFor(tool.progress());
            long next = plugin.plan().thresholdFor(tier + 1);
            String enchants = GrowthItems.enchantSummary(plugin, tool, tier);
            sender.sendMessage(Text.mm(
                    "<gold>Growth Pickaxe <name></gold>: tier=<tier> progress=<progress>/<next> enchants=<enchants> branch=<branch> status=<status> epoch=<epoch> netherite=<nt>",
                    "name", tool.ownerName(),
                    "tier", GrowthItems.tierName(tier, tool.netheriteUpgraded()),
                    "progress", String.valueOf(tool.progress()), "next", String.valueOf(next),
                    "enchants", enchants, "branch", tool.branch().name(), "status", tool.status().name(),
                    "epoch", String.valueOf(tool.epoch()), "nt", String.valueOf(tool.netheriteUpgraded())));
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("repair")) return repair(sender);
        if (sub.equals("reissue")) return reissue(sender, args);
        if (sub.equals("branch")) return branch(sender, args);
        if (sub.equals("upgrade-netherite")) return upgradeNetherite(sender);
        if (sub.equals("debug-on") || sub.equals("debug-off")) {
            if (!sender.hasPermission("wayfarer.main.admin")) return true;
            WayfarerMainPlugin.setDebug(sub.equals("debug-on"));
            sender.sendMessage(Text.mm(sub.equals("debug-on") ? "<green>Growth debug logging on."
                    : "<green>Growth debug logging off."));
            return true;
        }
        if (sub.equals("admin")) return admin(sender, args);
        sender.sendMessage(Text.mm(
                "<gold>Usage: /wayfarer-main [status|repair|reissue confirm|branch <fortune|silk>|upgrade-netherite]"));
        return true;
    }

    private boolean repair(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.mm("<red>Players only."));
            return true;
        }
        GrowthTool tool = plugin.toolOf(player.getUniqueId());
        if (tool == null) tool = plugin.repository().findActive(player.getUniqueId()).orElse(null);
        if (tool == null) {
            sender.sendMessage(Text.mm("<red>You have no Growth Pickaxe."));
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        GrowthTool.Identity id = GrowthItems.identify(plugin, held);
        boolean heldIsTool = id != null
                && id.toolId().equals(tool.toolId().toString())
                && id.epoch() == tool.epoch();
        boolean broken = tool.status() == GrowthTool.Status.BROKEN;
        if (!heldIsTool && !broken) {
            sender.sendMessage(Text.mm("<red>Hold your Growth Pickaxe to repair it."));
            return true;
        }
        int tier = plugin.tierFor(tool.progress());
        RepairPricing pricing = plugin.pricing();
        int damage = heldIsTool ? held.getDurability() : 0;
        int max = heldIsTool ? held.getType().getMaxDurability() : 1;
        long cost = broken ? pricing.brokenRepairCost(tier) : pricing.partialRepairCost(tier, damage, max);

        Economy economy = plugin.economy();
        if (!plugin.economyAvailable()) {
            // no free repairs without an economy provider
            sender.sendMessage(Text.mm("<red>Economy unavailable; repair cannot be processed."));
            return true;
        }
        if (cost > 0 && !economy.has(player, cost)) {
            sender.sendMessage(Text.mm("<red>Repair costs <white><cost></white> WM; you cannot afford it.",
                    "cost", String.valueOf(cost)));
            return true;
        }
        EconomyResponse response = economy.withdrawPlayer(player, cost);
        if (!response.transactionSuccess()) {
            sender.sendMessage(Text.mm("<red>Payment failed; nothing was changed."));
            return true;
        }
        GrowthTool repaired = tool.withStatus(GrowthTool.Status.ACTIVE);
        try {
            plugin.repository().update(repaired);
        } catch (Exception e) {
            plugin.getLogger().warning("repair DB update failed: " + e);
            economy.depositPlayer(player, cost); // refund; tool state unchanged
            sender.sendMessage(Text.mm("<red>Database error; you were not charged."));
            return true;
        }
        plugin.cacheTool(player, repaired);
        ItemStack restored = Items.rebuildPreservingDamage(plugin, repaired, heldIsTool ? held : null);
        if (heldIsTool) {
            player.getInventory().setItemInMainHand(restored);
        } else {
            plugin.giveCurrentItem(player, repaired);
        }
        sender.sendMessage(Text.mm("<green>Repaired for <white><cost></white> WM.", "cost", String.valueOf(cost)));
        return true;
    }

    /**
     * P-10: reissue keeps the same logical tool_id and bumps the epoch in place via a
     * conditional UPDATE, so the tool can never vanish or collide with its own PK.
     * Concurrency, double-charge and DB failure are all handled explicitly.
     */
    private boolean reissue(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.mm("<red>Players only."));
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage(Text.mm("<gold>Confirm with /wayfarer-main reissue confirm"));
            return true;
        }
        if (!plugin.tryReissueLock(player)) {
            sender.sendMessage(Text.mm("<red>A reissue is already in progress."));
            return true;
        }
        try {
            GrowthTool tool = plugin.toolOf(player.getUniqueId());
            if (tool == null) tool = plugin.repository().findActive(player.getUniqueId()).orElse(null);
            if (tool == null) {
                sender.sendMessage(Text.mm("<red>You have no Growth Pickaxe."));
                return true;
            }
            int tier = plugin.tierFor(tool.progress());
            RepairPricing pricing = plugin.pricing();
            boolean broken = tool.status() == GrowthTool.Status.BROKEN;
            long cost = pricing.reissueCost(tier, broken);

            if (!plugin.economyAvailable()) {
                sender.sendMessage(Text.mm("<red>Economy unavailable; reissue cannot be processed."));
                return true;
            }
            Economy economy = plugin.economy();
            if (!economy.has(player, cost)) {
                sender.sendMessage(Text.mm("<red>Reissue costs <white><cost></white> WM; you cannot afford it.",
                        "cost", String.valueOf(cost)));
                return true;
            }
            EconomyResponse response = economy.withdrawPlayer(player, cost);
            if (!response.transactionSuccess()) {
                sender.sendMessage(Text.mm("<red>Payment failed; nothing was changed."));
                return true;
            }
            // conditional epoch bump: 0 rows means a concurrent reissue already applied
            int rows = plugin.repository().bumpEpoch(tool.toolId(), tool.epoch());
            if (rows == 0) {
                economy.depositPlayer(player, cost);
                sender.sendMessage(Text.mm("<red>Tool changed in the meantime; you were not charged."));
                return true;
            }
            GrowthTool fresh = plugin.repository().findByToolId(tool.toolId());
            if (fresh == null) {
                economy.depositPlayer(player, cost);
                sender.sendMessage(Text.mm("<red>Tool missing; you were not charged. Contact an admin."));
                return true;
            }
            try {
                plugin.cacheTool(player, fresh);
                removeStaleCopies(player, fresh);
                plugin.giveCurrentItem(player, fresh);
            } catch (Exception e) {
                // the DB state is already the new epoch; keep it recoverable, never brick
                plugin.getLogger().warning("reissue delivery failed: " + e);
                sender.sendMessage(Text.mm(
                        "<gold>Reissue applied (charged) but delivery failed; rejoin to receive the item."));
                return true;
            }
            sender.sendMessage(Text.mm(
                    "<green>Reissued for <white><cost></white> WM (epoch <epoch>); progress, branch and upgrades kept.",
                    "cost", String.valueOf(cost), "epoch", String.valueOf(fresh.epoch())));
            return true;
        } finally {
            plugin.releaseReissueLock(player);
        }
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
        String arg = args[1].toLowerCase();
        if (arg.equals("silk") || arg.equals("silktouch") || arg.equals("silk_touch")) {
            branch = EvolutionPlan.Branch.SILK_TOUCH;
        } else if (arg.equals("fortune")) {
            branch = EvolutionPlan.Branch.FORTUNE;
        } else {
            sender.sendMessage(Text.mm("<red>Unknown branch. Use fortune or silk."));
            return true;
        }
        GrowthTool tool = plugin.toolOf(player.getUniqueId());
        if (tool == null) tool = plugin.repository().findActive(player.getUniqueId()).orElse(null);
        if (tool == null) {
            sender.sendMessage(Text.mm("<red>You have no Growth Pickaxe."));
            return true;
        }
        if (tool.branch() == branch) {
            sender.sendMessage(Text.mm("<gray>Branch already <branch>.", "branch", branch.name()));
            return true;
        }
        GrowthTool updated = tool.withBranch(branch);
        try {
            plugin.repository().update(updated);
        } catch (Exception e) {
            plugin.getLogger().warning("branch DB update failed: " + e);
            sender.sendMessage(Text.mm("<red>Database error; nothing was changed."));
            return true;
        }
        plugin.cacheTool(player, updated);
        // only replace the held item when it really is the player's current tool
        ItemStack held = player.getInventory().getItemInMainHand();
        GrowthTool.Identity id = GrowthItems.identify(plugin, held);
        if (id != null && id.toolId().equals(tool.toolId().toString()) && id.epoch() == tool.epoch()) {
            player.getInventory().setItemInMainHand(
                    Items.rebuildPreservingDamage(plugin, updated, held));
        }
        sender.sendMessage(Text.mm("<green>Branch set to <branch>.", "branch", branch.name()));
        return true;
    }

    /** Persistent, WM-gated Netherite upgrade (concept §14.1/§14.3; P-19). */
    private boolean upgradeNetherite(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.mm("<red>Players only."));
            return true;
        }
        GrowthTool tool = plugin.toolOf(player.getUniqueId());
        if (tool == null) tool = plugin.repository().findActive(player.getUniqueId()).orElse(null);
        if (tool == null) {
            sender.sendMessage(Text.mm("<red>You have no Growth Pickaxe."));
            return true;
        }
        if (tool.netheriteUpgraded()) {
            sender.sendMessage(Text.mm("<gray>Netherite Upgrade is already unlocked."));
            return true;
        }
        int tier = plugin.tierFor(tool.progress());
        if (tier < EvolutionPlan.DIAMOND) {
            sender.sendMessage(Text.mm("<red>The Netherite Upgrade requires a Diamond-tier tool."));
            return true;
        }
        if (!plugin.economyAvailable()) {
            sender.sendMessage(Text.mm("<red>Economy unavailable; the upgrade cannot be processed."));
            return true;
        }
        long cost = plugin.getConfig().getLong("netherite-upgrade-cost", 5000);
        Economy economy = plugin.economy();
        if (!economy.has(player, cost)) {
            sender.sendMessage(Text.mm("<red>The upgrade costs <white><cost></white> WM; you cannot afford it.",
                    "cost", String.valueOf(cost)));
            return true;
        }
        EconomyResponse response = economy.withdrawPlayer(player, cost);
        if (!response.transactionSuccess()) {
            sender.sendMessage(Text.mm("<red>Payment failed; nothing was changed."));
            return true;
        }
        int rows = plugin.repository().markNetheriteUpgraded(tool.toolId());
        if (rows == 0) {
            economy.depositPlayer(player, cost);
            sender.sendMessage(Text.mm("<red>Already unlocked; you were not charged."));
            return true;
        }
        GrowthTool updated = tool.withNetheriteUpgraded(true);
        plugin.cacheTool(player, updated);
        ItemStack held = player.getInventory().getItemInMainHand();
        GrowthTool.Identity id = GrowthItems.identify(plugin, held);
        if (id != null && id.toolId().equals(tool.toolId().toString()) && id.epoch() == tool.epoch()) {
            player.getInventory().setItemInMainHand(
                    Items.rebuildPreservingDamage(plugin, updated, held));
        } else {
            plugin.giveCurrentItem(player, updated);
        }
        sender.sendMessage(Text.mm("<light_purple>Netherite Upgrade unlocked for <white><cost></white> WM!",
                "cost", String.valueOf(cost)));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wayfarer.main.admin")) {
            sender.sendMessage(Text.mm("<red>You may not do that."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.mm(
                    "<gold>Usage: /wayfarer-main admin <grant|setprogress|revoke|netherite> <player> [args]"));
            return true;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
        if (target == null) {
            sender.sendMessage(Text.mm("<red>Player must be online."));
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "grant" -> {
                plugin.ensureFirstToolTarget(target);
                GrowthTool tool = plugin.repository().findActive(target.getUniqueId()).orElse(null);
                if (tool != null) {
                    plugin.cacheTool(target, tool);
                    plugin.giveCurrentItem(target, tool);
                }
                sender.sendMessage(Text.mm("<green>Granted Growth Pickaxe to <name>.", "name", target.getName()));
            }
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
                plugin.cacheTool(target, updated);
                plugin.giveCurrentItem(target, updated);
                sender.sendMessage(Text.mm("<green>Progress set for <name>.", "name", target.getName()));
            }
            case "revoke" -> {
                GrowthTool tool = plugin.repository().findActive(target.getUniqueId()).orElse(null);
                if (tool == null) return true;
                plugin.repository().update(tool.withStatus(GrowthTool.Status.REVOKED));
                sender.sendMessage(Text.mm("<green>Revoked <name>'s pickaxe.", "name", target.getName()));
            }
            case "netherite" -> {
                GrowthTool tool = plugin.repository().findActive(target.getUniqueId()).orElse(null);
                if (tool == null) return true;
                if (args.length >= 4 && args[3].equalsIgnoreCase("true")) {
                    plugin.repository().markNetheriteUpgraded(tool.toolId());
                    sender.sendMessage(Text.mm("<green>Netherite upgrade forced on.", "name", target.getName()));
                } else {
                    sender.sendMessage(Text.mm("<gray>Use: admin netherite <player> true"));
                }
            }
            default -> sender.sendMessage(Text.mm("<red>Unknown admin subcommand."));
        }
        return true;
    }

    private void removeStaleCopies(Player player, GrowthTool fresh) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            GrowthTool.Identity id = GrowthItems.identify(plugin, item);
            if (id != null && id.toolId().equals(fresh.toolId().toString()) && id.epoch() != fresh.epoch()) {
                player.getInventory().setItem(i, null);
            }
        }
    }
}
