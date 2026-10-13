package io.github.eariver.wayfarer.main.gui;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.main.WayfarerMainPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /wayfarer-shop — always available; doubles as the /shop fallback when no shop plugin exists. */
public final class ShopCommand implements CommandExecutor {
    private final WayfarerMainPlugin plugin;

    public ShopCommand(WayfarerMainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.mm("<red>Players only."));
            return true;
        }
        plugin.shopGui().open(player);
        return true;
    }
}
