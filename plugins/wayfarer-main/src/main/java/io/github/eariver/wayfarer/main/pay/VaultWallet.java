package io.github.eariver.wayfarer.main.pay;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

/**
 * Vault adapter for {@link GrowthPaidOps.Wallet}: every movement is confirmed through
 * {@link EconomyResponse#transactionSuccess()} so a "success" can never be assumed.
 */
public final class VaultWallet implements GrowthPaidOps.Wallet {

    private final Economy economy;
    private final OfflinePlayer player;

    public VaultWallet(Economy economy, OfflinePlayer player) {
        this.economy = economy;
        this.player = player;
    }

    @Override
    public boolean has(long amount) {
        return economy != null && player != null && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(long amount) {
        if (economy == null || player == null) return false;
        return confirmed(economy.withdrawPlayer(player, amount));
    }

    @Override
    public boolean deposit(long amount) {
        if (economy == null || player == null) return false;
        return confirmed(economy.depositPlayer(player, amount));
    }

    private static boolean confirmed(EconomyResponse response) {
        return response != null && response.transactionSuccess();
    }
}
