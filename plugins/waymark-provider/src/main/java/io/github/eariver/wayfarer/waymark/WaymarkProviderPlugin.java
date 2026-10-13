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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared Waymark (WM) economy provider. PoC storage is one shared SQLite ledger file
 * (WAL) on a path common to Main and Frontier; the production equivalent is the
 * Redis-backed RedisEconomy service (see docs/DECISIONS.md D-2026-10-11-03).
 *
 * <p>Money movement is always provider-verified (M3): the local ledger executes a
 * transfer inside one transaction and every external Vault call checks its
 * {@code EconomyResponse}.
 */
public final class WaymarkProviderPlugin extends JavaPlugin {

    private WaymarkLedger ledger;
    private String serverId;
    private boolean externalEconomy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        serverId = getConfig().getString("server-id", "poc").replace(" ", "-");
        File dbFile = new File(getConfig().getString("storage.file", "waymark.db"));
        Db db = new Db(dbFile, getConfig().getBoolean("storage.shared", true));
        ledger = new WaymarkLedger(db, serverId);
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
        if (ledger != null) ledger.store().close();
    }

    public boolean externalEconomy() {
        return externalEconomy;
    }

    public net.milkbowl.vault.economy.Economy vaultProvider() {
        var reg = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        return reg != null ? reg.getProvider() : null;
    }

    public WaymarkLedger ledger() {
        return ledger;
    }

    public Db store() {
        return ledger.store();
    }

    public String serverId() {
        return serverId;
    }

    public long balance(UUID holder) {
        return ledger.balance(holder);
    }

    public WaymarkResult deposit(UUID target, long amount, String kind, String reference) {
        return ledger.deposit(target, amount, kind, reference);
    }

    public WaymarkResult withdraw(UUID actor, UUID target, long amount, String kind, String reference) {
        return ledger.withdraw(actor, target, amount, kind, reference);
    }

    /** Single-transaction player transfer (local ledger path). */
    public WaymarkResult transfer(UUID from, UUID to, long amount) {
        return ledger.transfer(from, to, amount, "pay");
    }

    public boolean setBalance(UUID target, long amount, String kind) {
        return ledger.setBalance(target, amount, kind);
    }

    /**
     * Vault adapter whose party lookup never needs an online player: the parties passed
     * in are used first and only an unseen holder falls back to the offline lookup.
     */
    public record VaultAdapter(net.milkbowl.vault.economy.Economy economy, Map<UUID, OfflinePlayer> parties)
            implements WaymarkTransfer.Provider {

        public VaultAdapter(net.milkbowl.vault.economy.Economy economy, OfflinePlayer... parties) {
            this(economy, Stream.of(parties).filter(Objects::nonNull)
                    .collect(Collectors.toMap(OfflinePlayer::getUniqueId, p -> p, (a, b) -> a)));
        }

        private OfflinePlayer party(UUID holder) {
            OfflinePlayer party = parties.get(holder);
            return party != null ? party : Bukkit.getOfflinePlayer(holder);
        }

        @Override
        public boolean has(UUID holder, long amount) {
            return economy.has(party(holder), amount);
        }

        @Override
        public boolean withdraw(UUID holder, long amount) {
            return confirmed(economy.withdrawPlayer(party(holder), amount));
        }

        @Override
        public boolean deposit(UUID holder, long amount) {
            return confirmed(economy.depositPlayer(party(holder), amount));
        }

        private static boolean confirmed(net.milkbowl.vault.economy.EconomyResponse response) {
            return response != null && response.transactionSuccess();
        }
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
                    bal = provider != null && offline != null ? (long) provider.getBalance(offline) : -1;
                } else {
                    bal = plugin.balance(target);
                }
                if (bal < 0) {
                    sender.sendMessage(Text.mm("<red>Economy provider unavailable."));
                    return true;
                }
                sender.sendMessage(Text.mm("<gold>Waymark balance: <white><amount></white> WM",
                        "amount", String.valueOf(bal)));
                return true;
            }
            if (args[0].equalsIgnoreCase("pay") && args.length == 3) {
                return pay(sender, args);
            }
            if (List.of("give", "take", "set").contains(args[0].toLowerCase()) && args.length == 3) {
                return admin(sender, args);
            }
            sender.sendMessage(Text.mm("<gold>Usage: /waymark [balance|pay <player> <amount>]"));
            return true;
        }

        /**
         * M3: validate both UUIDs, keep the P-11 self-payment rejection, confirm every
         * provider leg, and only report success when the transfer really completed.
         */
        private boolean pay(CommandSender sender, String[] args) {
            if (!(sender instanceof Player from)) {
                sender.sendMessage(Text.mm("<red>Players only."));
                return true;
            }
            UUID fromUuid = from.getUniqueId();
            if (fromUuid == null) {
                sender.sendMessage(Text.mm("<red>Your account could not be identified."));
                return true;
            }
            OfflinePlayer to = Bukkit.getOfflinePlayer(args[1]);
            UUID toUuid = to != null ? to.getUniqueId() : null;
            if (toUuid == null) {
                sender.sendMessage(Text.mm("<red>Unknown player.", "name", args[1]));
                return true;
            }
            // P-11: self-payment never moves currency and must be rejected.
            if (toUuid.equals(fromUuid)) {
                sender.sendMessage(Text.mm("<red>You cannot pay yourself."));
                return true;
            }
            Long amount = parseAmount(sender, args[2]);
            if (amount == null) return true;
            if (amount <= 0) {
                sender.sendMessage(Text.mm("<red>Amount must be positive."));
                return true;
            }

            if (plugin.externalEconomy()) {
                var provider = plugin.vaultProvider();
                if (provider == null) {
                    sender.sendMessage(Text.mm("<red>Economy provider unavailable; nothing was sent."));
                    return true;
                }
                WaymarkTransfer.Provider adapter = new WaymarkProviderPlugin.VaultAdapter(provider, from, to);
                WaymarkTransfer.Outcome out = WaymarkTransfer.external(fromUuid, toUuid, amount, adapter);
                switch (out.status()) {
                    case OK -> sender.sendMessage(Text.mm("<green>Sent <white><amount></white> WM to <name>.",
                            "amount", String.valueOf(amount), "name", args[1]));
                    case SELF_PAYMENT -> sender.sendMessage(Text.mm("<red>You cannot pay yourself."));
                    case INVALID_TARGET -> sender.sendMessage(Text.mm("<red>Unknown player.", "name", args[1]));
                    case INVALID_AMOUNT -> sender.sendMessage(Text.mm("<red>Amount must be positive."));
                    case PROVIDER_UNAVAILABLE ->
                            sender.sendMessage(Text.mm("<red>Economy provider unavailable; nothing was sent."));
                    case INSUFFICIENT_FUNDS -> sender.sendMessage(Text.mm("<red>Payment failed: insufficient funds"));
                    case WITHDRAW_FAILED -> sender.sendMessage(Text.mm(
                            "<red>The provider did not confirm the withdrawal; nothing was sent. "
                                    + "Keep this reference for an admin: <ref>", "ref", out.reference()));
                    case DEPOSIT_FAILED_REFUNDED -> sender.sendMessage(Text.mm(
                            "<red>The deposit failed after the withdrawal; your WM was returned. "
                                    + "Nothing was sent."));
                    case DEPOSIT_FAILED_REFUND_FAILED -> sender.sendMessage(Text.mm(
                            "<red>The deposit failed AND the refund failed. "
                                    + "Keep this reference for an admin: <ref>", "ref", out.reference()));
                }
                return true;
            }

            WaymarkResult r = plugin.transfer(fromUuid, toUuid, amount);
            if (!r.ok()) {
                sender.sendMessage(Text.mm("<red>Payment failed: <error>", "error", r.error()));
                return true;
            }
            sender.sendMessage(Text.mm("<green>Sent <white><amount></white> WM to <name>.",
                    "amount", String.valueOf(amount), "name", args[1]));
            return true;
        }

        /** Admin give/take/set with verified results on both the local and the Vault path. */
        private boolean admin(CommandSender sender, String[] args) {
            if (!sender.hasPermission("waymark.admin")) {
                sender.sendMessage(Text.mm("<red>You may not do that."));
                return true;
            }
            OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
            if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[1]);
            UUID uuid = target != null ? target.getUniqueId() : null;
            if (uuid == null) {
                sender.sendMessage(Text.mm("<red>Unknown player. Use an online or previously seen player."));
                return true;
            }
            Long amount = parseAmount(sender, args[2]);
            if (amount == null) return true;
            String kind = args[0].toLowerCase();
            boolean nonNegativeOnly = kind.equals("set");
            if (amount < 0 || (!nonNegativeOnly && amount == 0)) {
                sender.sendMessage(Text.mm(nonNegativeOnly
                        ? "<red>Amount cannot be negative."
                        : "<red>Amount must be positive."));
                return true;
            }

            long balance;
            if (plugin.externalEconomy()) {
                var provider = plugin.vaultProvider();
                if (provider == null) {
                    sender.sendMessage(Text.mm("<red>Economy provider unavailable."));
                    return true;
                }
                OfflinePlayer party = target != null ? target : Bukkit.getOfflinePlayer(uuid);
                WaymarkTransfer.Provider adapter = new WaymarkProviderPlugin.VaultAdapter(provider, party);
                boolean ok = switch (kind) {
                    case "give" -> WaymarkTransfer.adminGive(adapter, uuid, amount);
                    case "take" -> WaymarkTransfer.adminTake(adapter, uuid, amount);
                    default -> WaymarkTransfer.adminSet(adapter, uuid, amount,
                            u -> (long) provider.getBalance(party));
                };
                if (!ok) {
                    sender.sendMessage(Text.mm("<red>Operation failed (insufficient funds or provider refused)."));
                    return true;
                }
                balance = (long) provider.getBalance(party);
            } else {
                boolean ok = switch (kind) {
                    case "give" -> plugin.deposit(uuid, amount, "admin", sender.getName()).ok();
                    case "take" -> plugin.withdraw(null, uuid, amount, "admin", sender.getName()).ok();
                    default -> plugin.setBalance(uuid, amount, "admin");
                };
                if (!ok) {
                    sender.sendMessage(Text.mm("<red>Operation failed."));
                    return true;
                }
                balance = plugin.balance(uuid);
            }
            sender.sendMessage(Text.mm("<green><kind> <amount> WM for <name>; balance now <balance> WM.",
                    "kind", kind, "amount", String.valueOf(Math.abs(amount)), "name",
                    String.valueOf(target.getName()), "balance", String.valueOf(balance)));
            return true;
        }

        private Long parseAmount(CommandSender sender, String raw) {
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                sender.sendMessage(Text.mm("<red>Amount must be a whole number of WM."));
                return null;
            }
        }

        private UUID lookupUuid(String name) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null) return p.getUniqueId();
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            return cached != null ? cached.getUniqueId() : null;
        }
    }
}
