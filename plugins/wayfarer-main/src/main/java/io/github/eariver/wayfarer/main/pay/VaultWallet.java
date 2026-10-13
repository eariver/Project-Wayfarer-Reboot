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
    public GrowthPaidOps.Confirmation withdraw(long amount) {
        return confirm(() -> economy.withdrawPlayer(player, amount));
    }

    @Override
    public GrowthPaidOps.Confirmation deposit(long amount) {
        return confirm(() -> economy.depositPlayer(player, amount));
    }

    /** R-04: exceptions and null responses are UNKNOWN, never a plain failure. */
    private GrowthPaidOps.Confirmation confirm(java.util.function.Supplier<EconomyResponse> leg) {
        if (economy == null || player == null) return GrowthPaidOps.Confirmation.UNKNOWN;
        try {
            EconomyResponse response = leg.get();
            if (response == null) return GrowthPaidOps.Confirmation.UNKNOWN;
            return response.transactionSuccess()
                    ? GrowthPaidOps.Confirmation.OK
                    : GrowthPaidOps.Confirmation.FAILED;
        } catch (RuntimeException e) {
            return GrowthPaidOps.Confirmation.UNKNOWN;
        }
    }
}
