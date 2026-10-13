package io.github.eariver.wayfarer.frontier.cmd;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.frontier.WayfarerFrontierPlugin;
import io.github.eariver.wayfarer.frontier.item.ThemeItems;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /wayfarer-frontier command tree. */
public final class FrontierCommand implements CommandExecutor {

    private final WayfarerFrontierPlugin plugin;
    private final ThemeItems items;

    /** Find the nearest ground block near the player with 3 blocks of clearance. */
    private Block findOpenGround(Player player) {
        var base = player.getLocation();
        for (int dx = 2; dx <= 8; dx += 2) {
            for (int dz = 0; dz <= 6; dz += 3) {
                Block ground = base.clone().add(dx, -1, dz).getBlock();
                if (ground.getType().isSolid()) {
                    Block a1 = ground.getRelative(0, 1, 0);
                    Block a2 = a1.getRelative(0, 1, 0);
                    Block a3 = a2.getRelative(0, 1, 0);
                    if (a1.getType().isAir() && a2.getType().isAir() && a3.getType().isAir()) {
                        return ground;
                    }
                }
            }
        }
        return null;
    }
    public FrontierCommand(WayfarerFrontierPlugin plugin) {
        this.plugin = plugin;
        this.items = plugin.themeItems();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.mm("<red>Players only."));
                return true;
            }
            plugin.navGui().open(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("shop")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.mm("<red>Players only."));
                return true;
            }
            plugin.shopGui().open(player);
            return true;
        }
        if (sub.equals("waystones")) {
            if (!(sender instanceof Player player)) return true;
            var list = plugin.waystones().teleportable(player.getUniqueId(), player.getWorld().getName());
            if (list.isEmpty()) {
                player.sendMessage(Text.mm("<gray>No discovered waystones here."));
                return true;
            }
            for (var w : list) {
                player.sendMessage(Text.mm("<gold>" + plugin.waystones().displayName(w)
                        + " <gray>(" + w.x() + "," + w.y() + "," + w.z() + ")"));
            }
            return true;
        }
        if (sub.equals("revive") || sub.equals("maintain") || sub.equals("overwrite")) {
            if (!(sender instanceof Player player)) return true;
            var core = player.getTargetBlockExact(6);
            if (core == null || !plugin.waystones().isWaystoneCore(core)) {
                player.sendMessage(Text.mm("<red>Look at the waystone core."));
                return true;
            }
            plugin.waystones().lifecycleOperation(player, core, sub);
            return true;
        }
        if (sub.equals("gate") && args.length >= 2) {
            // R-05: moving ANOTHER player is an administrative action. Without the
            // permission any player could drag anyone (including ops) to the gate.
            if (!FrontierGate.mayTeleportOther(sender.hasPermission("wayfarer.frontier.admin"))) {
                sender.sendMessage(Text.mm("<red>You may not do that."));
                return true;
            }
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Text.mm("<red>Player offline."));
                return true;
            }
            if (target.equals(sender)) {
                // self-teleport stays available to everyone via the navigation GUI
                sender.sendMessage(Text.mm("<red>Use /wayfarer-frontier open to travel yourself."));
                return true;
            }
            boolean moved = target.teleport(plugin.gateArrival());
            if (!moved) {
                sender.sendMessage(Text.mm("<red>Teleport failed; <name> was not moved.", "name", target.getName()));
                return true;
            }
            sender.sendMessage(Text.mm("<green>Sent <name> to the Frontier gate.", "name", target.getName()));
            return true;
        }
        if (sub.equals("grant") && args.length >= 3) {
            if (!sender.hasPermission("wayfarer.frontier.admin")) return true;
            Player target = plugin.getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Text.mm("<red>Player offline."));
                return true;
            }
            ThemeItems items = plugin.themeItems();
            ThemeItems.ItemType type;
            try {
                type = ThemeItems.ItemType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Text.mm("<red>Unknown item type."));
                return true;
            }
            ItemStack item = items.build(type, target.getUniqueId(), "worlds_beyond", 0);
            items.stamp(item, type, target.getUniqueId(), "worlds_beyond", 0);
            if (type == ThemeItems.ItemType.LAUNCHPAD) item.setAmount(2);
            target.getInventory().addItem(item);
            sender.sendMessage(Text.mm("<green>Granted <type> to <name>.", "type", type.name(), "name", target.getName()));
            return true;
        }
        if (sub.equals("debug-state") && args.length >= 5) {
            if (!sender.hasPermission("wayfarer.frontier.admin")) return true;
            long id = Long.parseLong(args[1]);
            boolean ok = plugin.waystones().forceState(id,
                    Long.parseLong(args[2]), Long.parseLong(args[3]), Long.parseLong(args[4]));
            sender.sendMessage(Text.mm(ok ? "<green>State forced." : "<red>Not found."));
            return true;
        }
        if (sub.equals("buildgates")) {
            if (!sender.hasPermission("wayfarer.frontier.admin")) return true;
            plugin.buildGatesIfNeeded();
            sender.sendMessage(Text.mm("<green>Gate structures ensured."));
            return true;
        }
        // Verification-only simulation: exercises the real domain logic with the player's
        // target block without relying on client interaction packets.
        if (sub.equals("admin-simulate") && args.length >= 3 && args[1].equalsIgnoreCase("waystone-place")) {
            if (!sender.hasPermission("wayfarer.frontier.admin")) return true;
            Player target = plugin.getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Text.mm("<red>Player offline."));
                return true;
            }
            Block block = findOpenGround(target);
            if (block == null) { sender.sendMessage(Text.mm("<red>No open ground found.")); return true; }
            ItemStack tool = items.build(ThemeItems.ItemType.WAYSTONE_TOOL, target.getUniqueId(), "worlds_beyond", 0);
            items.stamp(tool, ThemeItems.ItemType.WAYSTONE_TOOL, target.getUniqueId(), "worlds_beyond", 0);
            target.getInventory().setItemInMainHand(tool);
            plugin.waystones().attemptPlacement(target, block);
            sender.sendMessage(Text.mm("<green>simulated waystone placement for <name> at <pos>",
                    "name", target.getName(), "pos", block.getX() + "," + block.getY() + "," + block.getZ()));
            return true;
        }
        if (sub.equals("admin-simulate") && args.length >= 3 && args[1].equalsIgnoreCase("launchpad-place")) {
            if (!sender.hasPermission("wayfarer.frontier.admin")) return true;
            Player target = plugin.getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Text.mm("<red>Player offline."));
                return true;
            }
            Block block = findOpenGround(target);
            if (block == null) { sender.sendMessage(Text.mm("<red>No open ground found.")); return true; }
            ItemStack tool = items.build(ThemeItems.ItemType.LAUNCHPAD, target.getUniqueId(), "worlds_beyond", 0);
            items.stamp(tool, ThemeItems.ItemType.LAUNCHPAD, target.getUniqueId(), "worlds_beyond", 0);
            target.getInventory().setItemInMainHand(tool);
            plugin.launchpads().attemptPlacement(target, block, tool);
            sender.sendMessage(Text.mm("<green>simulated launchpad placement for <name> at <pos>",
                    "name", target.getName(), "pos", block.getX() + "," + block.getY() + "," + block.getZ()));
            return true;
        }
        sender.sendMessage(Text.mm("<gold>Usage: /wayfarer-frontier [open|shop|waystones|revive|maintain|overwrite|grant|debug-state|buildgates|admin-simulate]"));
        return true;
    }
}
