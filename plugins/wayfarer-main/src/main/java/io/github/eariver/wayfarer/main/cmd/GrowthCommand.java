package io.github.eariver.wayfarer.main.cmd;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.main.GrowthItems;
import io.github.eariver.wayfarer.main.Items;
import io.github.eariver.wayfarer.main.WayfarerMainPlugin;
import io.github.eariver.wayfarer.main.domain.EvolutionPlan;
import io.github.eariver.wayfarer.main.domain.GrowthTool;
import io.github.eariver.wayfarer.main.domain.RepairPricing;
import io.github.eariver.wayfarer.main.gui.GrowthGui;
import io.github.eariver.wayfarer.main.pay.GrowthPaidOps;
import io.github.eariver.wayfarer.main.pay.VaultWallet;
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
    private final GrowthPaidOps.ToolStore store;

    public GrowthCommand(WayfarerMainPlugin plugin) {
        this.plugin = plugin;
        // the repository already satisfies the ToolStore surface (bumpEpoch, markNetheriteUpgraded, findTool, update)
        this.store = plugin.repository();
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

        if (!plugin.economyAvailable()) {
            // no free repairs without an economy provider
            sender.sendMessage(Text.mm("<red>Economy unavailable; repair cannot be processed."));
            return true;
        }
        GrowthPaidOps.Wallet wallet = wallet(player);
        final GrowthTool repaired = tool.withStatus(GrowthTool.Status.ACTIVE);
        final ItemStack heldItem = held;
        final boolean holdsTool = heldIsTool;
        GrowthPaidOps.Delivery deliver = () -> {
            plugin.cacheTool(player, repaired);
            ItemStack restored = Items.rebuildPreservingDamage(plugin, repaired, holdsTool ? heldItem : null);
            if (holdsTool) {
                player.getInventory().setItemInMainHand(restored);
            } else {
                plugin.giveCurrentItem(player, repaired);
            }
        };
        GrowthPaidOps.Result result = GrowthPaidOps.repair(store, wallet, tool, cost, deliver, plugin.getLogger());
        sendRepairOutcome(player, cost, result);
        return true;
    }

    /** Shared repair outcome wording: never claim "not charged" unless the refund was confirmed. */
    private void sendRepairOutcome(Player player, long cost, GrowthPaidOps.Result result) {
        switch (result.state()) {
            case PAYMENT_DECLINED -> player.sendMessage(Text.mm(
                    "<red>Repair costs <white><cost></white> WM; you cannot afford it.",
                    "cost", String.valueOf(cost)));
            case PAYMENT_FAILED -> player.sendMessage(Text.mm("<red>Payment failed; nothing was changed."));
            case COMPLETED -> player.sendMessage(Text.mm(
                    "<green>Repaired for <white><cost></white> WM.", "cost", String.valueOf(cost)));
            case APPLIED_DELIVERY_FAILED -> player.sendMessage(Text.mm(
                    "<gold>Repair applied (charged) but the item could not be handed over; rejoin to receive it."));
            case REFUNDED -> player.sendMessage(Text.mm(
                    "<red>Database error; you were not charged."));
            case REFUND_FAILED -> player.sendMessage(Text.mm(
                    "<red>Repair was charged but the refund FAILED. Keep this reference for an admin: <ref>",
                    "ref", result.reference()));
            case UNKNOWN -> player.sendMessage(Text.mm(
                    "<gold>You were charged, but the result could not be verified. Rejoin to refresh the tool; "
                            + "if it is unchanged, give an admin this reference: <ref>",
                    "ref", result.reference()));
        }
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
            GrowthPaidOps.Wallet wallet = wallet(player);
            final GrowthTool reissueTool = tool;
            GrowthPaidOps.Delivery deliver = () -> {
                GrowthTool fresh = plugin.repository().findByToolId(reissueTool.toolId());
                if (fresh == null) throw new IllegalStateException("row vanished after the epoch bump");
                plugin.cacheTool(player, fresh);
                removeStaleCopies(player, fresh);
                plugin.giveCurrentItem(player, fresh);
            };
            GrowthPaidOps.Result result = GrowthPaidOps.reissue(
                    store, wallet, reissueTool, cost, deliver, plugin.getLogger());
            return sendReissueOutcome(player, cost, result);
        } finally {
            plugin.releaseReissueLock(player);
        }
    }

    /** Reissue wording derived from the paid-op classification; see GrowthPaidOps. */
    private boolean sendReissueOutcome(Player player, long cost, GrowthPaidOps.Result result) {
        switch (result.state()) {
            case PAYMENT_DECLINED -> player.sendMessage(Text.mm(
                    "<red>Reissue costs <white><cost></white> WM; you cannot afford it.",
                    "cost", String.valueOf(cost)));
            case PAYMENT_FAILED -> player.sendMessage(Text.mm("<red>Payment failed; nothing was changed."));
            case COMPLETED -> {
                GrowthTool fresh = plugin.toolOf(player.getUniqueId());
                int epoch = fresh != null ? fresh.epoch() : -1;
                player.sendMessage(Text.mm(
                        "<green>Reissued for <white><cost></white> WM (epoch <epoch>); progress, branch and upgrades kept.",
                        "cost", String.valueOf(cost), "epoch", String.valueOf(epoch)));
            }
            case APPLIED_DELIVERY_FAILED -> player.sendMessage(Text.mm(
                    "<gold>Reissue applied (charged) but the item could not be handed over; "
                            + "rejoin to receive it."));
            case REFUNDED -> {
                if (result.cause() == GrowthPaidOps.Cause.NO_MATCH) {
                    player.sendMessage(Text.mm("<red>Tool changed in the meantime; you were not charged."));
                } else {
                    player.sendMessage(Text.mm("<red>Database error; you were not charged."));
                }
            }
            case REFUND_FAILED -> player.sendMessage(Text.mm(
                    "<red>Reissue was charged but the refund FAILED. Keep this reference for an admin: <ref>",
                    "ref", result.reference()));
            case UNKNOWN -> player.sendMessage(Text.mm(
                    "<gold>You were charged, but the result could not be verified. Rejoin to refresh the tool; "
                            + "if it is unchanged, give an admin this reference: <ref>",
                    "ref", result.reference()));
        }
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
        // paid operations share one lock so a double click cannot double-charge/apply
        if (!plugin.tryReissueLock(player)) {
            sender.sendMessage(Text.mm("<red>Another paid operation is already in progress."));
            return true;
        }
        try {
            return upgradeNetheriteLocked(sender, player);
        } finally {
            plugin.releaseReissueLock(player);
        }
    }

    private boolean upgradeNetheriteLocked(CommandSender sender, Player player) {
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
        GrowthPaidOps.Wallet wallet = wallet(player);
        final GrowthTool upgradeTool = tool;
        GrowthPaidOps.Delivery deliver = () -> {
            // always work from the authoritative row so the item matches the DB
            GrowthTool updated = plugin.repository().findByToolId(upgradeTool.toolId());
            if (updated == null) throw new IllegalStateException("row vanished after the upgrade");
            if (!updated.netheriteUpgraded()) updated = updated.withNetheriteUpgraded(true);
            plugin.cacheTool(player, updated);
            ItemStack held = player.getInventory().getItemInMainHand();
            GrowthTool.Identity id = GrowthItems.identify(plugin, held);
            if (id != null && id.toolId().equals(upgradeTool.toolId().toString())
                    && id.epoch() == upgradeTool.epoch()) {
                player.getInventory().setItemInMainHand(
                        Items.rebuildPreservingDamage(plugin, updated, held));
            } else {
                plugin.giveCurrentItem(player, updated);
            }
        };
        GrowthPaidOps.Result result = GrowthPaidOps.upgradeNetherite(
                store, wallet, upgradeTool, cost, deliver, plugin.getLogger());
        switch (result.state()) {
            case PAYMENT_DECLINED -> sender.sendMessage(Text.mm(
                    "<red>The upgrade costs <white><cost></white> WM; you cannot afford it.",
                    "cost", String.valueOf(cost)));
            case PAYMENT_FAILED -> sender.sendMessage(Text.mm("<red>Payment failed; nothing was changed."));
            case COMPLETED -> sender.sendMessage(Text.mm(
                    "<light_purple>Netherite Upgrade unlocked for <white><cost></white> WM!",
                    "cost", String.valueOf(cost)));
            case APPLIED_DELIVERY_FAILED -> sender.sendMessage(Text.mm(
                    "<gold>Upgrade applied (charged) but the item could not be handed over; "
                            + "rejoin to receive it."));
            case REFUNDED -> {
                if (result.cause() == GrowthPaidOps.Cause.NO_MATCH) {
                    sender.sendMessage(Text.mm("<gray>Already unlocked; you were not charged."));
                } else {
                    sender.sendMessage(Text.mm("<red>Database error; you were not charged."));
                }
            }
            case REFUND_FAILED -> sender.sendMessage(Text.mm(
                    "<red>The upgrade was charged but the refund FAILED. Keep this reference for an admin: <ref>",
                    "ref", result.reference()));
            case UNKNOWN -> sender.sendMessage(Text.mm(
                    "<gold>You were charged, but the result could not be verified. Rejoin to refresh the tool; "
                            + "if it is unchanged, give an admin this reference: <ref>",
                    "ref", result.reference()));
        }
        return true;
    }

    private GrowthPaidOps.Wallet wallet(Player player) {
        return new VaultWallet(plugin.economy(), player);
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
