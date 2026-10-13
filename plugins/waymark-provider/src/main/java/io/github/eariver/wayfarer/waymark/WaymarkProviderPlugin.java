package io.github.eariver.wayfarer.waymark;

import io.github.eariver.wayfarer.common.Db;
import io.github.eariver.wayfarer.common.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Shared Waymark (WM) economy provider. PoC storage is one shared SQLite ledger file
 * (WAL) on a path common to Main and Frontier; the production equivalent is the
 * Redis-backed RedisEconomy service (see docs/DECISIONS.md D-2026-10-11-03).
 */
public final class WaymarkProviderPlugin extends JavaPlugin {

    private Db store;
    private String serverId;
    private boolean externalEconomy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        serverId = getConfig().getString("server-id", "poc").replace(" ", "-");
        File dbFile = new File(getConfig().getString("storage.file", "waymark.db"));
        store = new Db(dbFile, getConfig().getBoolean("storage.shared", true));
        store.exec(
                "CREATE TABLE IF NOT EXISTS wm_balance (" +
                        "holder TEXT PRIMARY KEY, balance INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS wm_ledger (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, ts INTEGER NOT NULL, server_id TEXT NOT NULL, " +
                        "kind TEXT NOT NULL, actor TEXT, target TEXT NOT NULL, amount INTEGER NOT NULL, " +
                        "reference TEXT)"
        );
        // When the production Redis-backed provider is present it registers first
        // (RedisEconomy loads at STARTUP); we then keep only the /waymark commands
        // and delegate all balance operations to the active Vault provider.
        boolean delegate = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class) != null;
        if (delegate) {
            getLogger().info("External economy provider detected; WaymarkProvider commands delegate to Vault (no local ledger)");
        } else if (false) {
            Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class,
                    new WaymarkEconomy(this), this, org.bukkit.plugin.ServicePriority.Highest);
        }
        externalEconomy = delegate;
        getServer().getPluginCommand("waymark").setExecutor(new WaymarkCommand(this));
        getLogger().info("Waymark economy ready (file=" + dbFile.getAbsolutePath() + ", server=" + serverId + ")");
    }

    @Override
    public void onDisable() {
        if (store != null) store.close();
    }

    public boolean externalEconomy() {
        return externalEconomy;
    }

    public net.milkbowl.vault.economy.Economy vaultProvider() {
        var reg = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        return reg != null ? reg.getProvider() : null;
    }

    public Db store() {
        return store;
    }

    public String serverId() {
        return serverId;
    }

    long now() {
        return System.currentTimeMillis() / 1000L;
    }

    public long balance(UUID holder) {
        return store.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM wm_balance WHERE holder=?")) {
                ps.setString(1, holder.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    public Result deposit(UUID target, long amount, String kind, String reference) {
        if (amount <= 0) return Result.error("amount must be positive");
        return store.tx(c -> {
            ensureRow(c, target);
            addBalance(c, target, amount);
            ledger(c, kind, null, target, amount, reference);
            return Result.ok(balance(target));
        });
    }

    public Result withdraw(UUID actor, UUID target, long amount, String kind, String reference) {
        if (amount <= 0) return Result.error("amount must be positive");
        return store.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM wm_balance WHERE holder=?")) {
                ps.setString(1, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getLong(1) < amount) {
                        return Result.error("insufficient funds");
                    }
                }
            }
            addBalance(c, target, -amount);
            ledger(c, kind, actor, target, -amount, reference);
            return Result.ok(balance(target));
        });
    }

    public boolean setBalance(UUID target, long amount, String kind) {
        return store.tx(c -> {
            ensureRow(c, target);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE wm_balance SET balance=?, updated_at=? WHERE holder=?")) {
                ps.setLong(1, amount);
                ps.setLong(2, now());
                ps.setString(3, target.toString());
                ps.executeUpdate();
            }
            ledger(c, kind, null, target, amount, "set");
            return true;
        });
    }

    private void ensureRow(Connection c, UUID target) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "MERGE INTO wm_balance (holder, balance, updated_at) KEY(holder) VALUES(?, 0, ?)")) {
            ps.setString(1, target.toString());
            ps.setLong(2, now());
            ps.executeUpdate();
        }
    }

    private void addBalance(Connection c, UUID target, long delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE wm_balance SET balance=balance+?, updated_at=? WHERE holder=?")) {
            ps.setLong(1, delta);
            ps.setLong(2, now());
            ps.setString(3, target.toString());
            ps.executeUpdate();
        }
    }

    private void ledger(Connection c, String kind, UUID actor, UUID target, long amount, String ref) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO wm_ledger(ts, server_id, kind, actor, target, amount, reference) VALUES(?,?,?,?,?,?,?)")) {
            ps.setLong(1, now());
            ps.setString(2, serverId);
            ps.setString(3, kind);
            ps.setString(4, actor == null ? null : actor.toString());
            ps.setString(5, target.toString());
            ps.setLong(6, amount);
            ps.setString(7, ref);
            ps.executeUpdate();
        }
    }

    public record Result(boolean ok, String error, long balance) {
        public static Result ok(long balance) {
            return new Result(true, null, balance);
        }

        public static Result error(String message) {
            return new Result(false, message, 0L);
        }
    }

    private record Pair(UUID actor, long amount) {
    }

    static final class WaymarkCommand implements CommandExecutor {
        private final WaymarkProviderPlugin plugin;

        WaymarkCommand(WaymarkProviderPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 0 || args[0].equalsIgnoreCase("balance") || args[0].equalsIgnoreCase("bal")) {
                UUID target = args.length >= 2 ? lookupUuid(args[1])
                        : sender instanceof Player p ? p.getUniqueId() : null;
                if (target == null) {
                    sender.sendMessage(Text.mm("<red>Specify a player."));
                    return true;
                }
                long bal;
                if (plugin.externalEconomy()) {
                    var provider = plugin.vaultProvider();
                    Player online = Bukkit.getPlayer(target);
                    OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayer(target);
                    bal = provider != null ? (long) provider.getBalance(offline) : 0;
                } else {
                    bal = plugin.balance(target);
                }
                sender.sendMessage(Text.mm("<gold>Waymark balance: <white><amount></white> WM",
                        "amount", String.valueOf(bal)));
                return true;
            }
            if (args[0].equalsIgnoreCase("pay") && args.length == 3) {
                if (!(sender instanceof Player from)) {
                    sender.sendMessage(Text.mm("<red>Players only."));
                    return true;
                }
                OfflinePlayer to = Bukkit.getOfflinePlayer(args[1]);
                if (to == null || to.getUniqueId() == null) {
                    sender.sendMessage(Text.mm("<red>Unknown player.", "name", args[1]));
                    return true;
                }
                // P-11: self-payment is a no-op that still logs a transfer; reject it.
                if (to.getUniqueId().equals(from.getUniqueId())) {
                    sender.sendMessage(Text.mm("<red>You cannot pay yourself."));
                    return true;
                }
                long amount;
                try {
                    amount = Long.parseLong(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Text.mm("<red>Amount must be a whole number of WM."));
                    return true;
                }
                if (amount <= 0) {
                    sender.sendMessage(Text.mm("<red>Amount must be positive."));
                    return true;
                }
                if (plugin.externalEconomy() && plugin.vaultProvider() != null) {
                    var provider = plugin.vaultProvider();
                    if (!provider.has(from, amount)) {
                        sender.sendMessage(Text.mm("<red>Payment failed: insufficient funds"));
                        return true;
                    }
                    provider.withdrawPlayer(from, amount);
                    provider.depositPlayer(to, amount);
                } else {
                    Result r = plugin.withdraw(from.getUniqueId(), from.getUniqueId(), amount, "pay", to.getUniqueId().toString());
                    if (!r.ok()) {
                        sender.sendMessage(Text.mm("<red>Payment failed: <error>", "error", r.error()));
                        return true;
                    }
                    plugin.deposit(to.getUniqueId(), amount, "pay", from.getUniqueId().toString());
                }
                sender.sendMessage(Text.mm("<green>Sent <white><amount></white> WM to <name>.",
                        "amount", String.valueOf(amount), "name", args[1]));
                return true;
            }
            if (List.of("give", "take", "set").contains(args[0].toLowerCase()) && args.length == 3) {
                if (!sender.hasPermission("waymark.admin")) {
                    sender.sendMessage(Text.mm("<red>You may not do that."));
                    return true;
                }
                OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
                if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null || target.getUniqueId() == null) {
                    sender.sendMessage(Text.mm("<red>Unknown player. Use an online or previously seen player."));
                    return true;
                }
                long amount;
                try {
                    amount = Long.parseLong(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Text.mm("<red>Amount must be a whole number of WM."));
                    return true;
                }
                String kind = args[0].toLowerCase();
                long balance;
                if (plugin.externalEconomy() && plugin.vaultProvider() != null) {
                    var provider = plugin.vaultProvider();
                    boolean ok = switch (kind) {
                        case "give" -> provider.depositPlayer(target, amount).transactionSuccess();
                        case "take" -> provider.withdrawPlayer(target, amount).transactionSuccess();
                        default -> {
                            double current = provider.getBalance(target);
                            if (current > amount) provider.withdrawPlayer(target, current - amount);
                            else if (current < amount) provider.depositPlayer(target, amount - current);
                            yield true;
                        }
                    };
                    if (!ok) {
                        sender.sendMessage(Text.mm("<red>Operation failed (insufficient funds?)."));
                        return true;
                    }
                    balance = (long) provider.getBalance(target);
                } else {
                    switch (kind) {
                        case "give" -> plugin.deposit(target.getUniqueId(), amount, "admin", sender.getName());
                        case "take" -> plugin.withdraw(null, target.getUniqueId(), amount, "admin", sender.getName());
                        case "set" -> plugin.setBalance(target.getUniqueId(), amount, "admin");
                    }
                    balance = plugin.balance(target.getUniqueId());
                }
                sender.sendMessage(Text.mm("<green><kind> <amount> WM for <name>; balance now <balance> WM.",
                        "kind", kind, "amount", String.valueOf(Math.abs(amount)), "name",
                        String.valueOf(target.getName()), "balance", String.valueOf(balance)));
                return true;
            }
            sender.sendMessage(Text.mm("<gold>Usage: /waymark [balance|pay <player> <amount>]"));
            return true;
        }

        private UUID lookupUuid(String name) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null) return p.getUniqueId();
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            return cached != null ? cached.getUniqueId() : null;
        }
    }
}
