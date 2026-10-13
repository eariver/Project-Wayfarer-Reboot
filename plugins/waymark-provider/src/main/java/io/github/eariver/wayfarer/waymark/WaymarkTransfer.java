package io.github.eariver.wayfarer.waymark;

import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * Pure orchestration for Waymark (WM) transfers over an external Vault provider (M3).
 *
 * <p>Every money movement is confirmed through the provider's response, so a success
 * message is only possible when both legs are verified. When a deposit fails after the
 * withdrawal, a compensating withdrawal of the same amount is attempted from the
 * recipient and its own result is reported — {@link Status#DEPOSIT_FAILED_REFUND_FAILED}
 * always needs manual reconciliation and is never reported as a sent transfer.
 *
 * <p>Known residual constraints (documented, not "fixed"): without exactly-once
 * semantics a crashing process between the two legs can leave the sender debited; the
 * ledger reference in {@link Outcome#reference()} is what an admin reconciles with. The
 * compensating withdrawal may also touch the recipient's own coins if the deposit partly
 * landed — the ledger-style single-transaction path ({@code WaymarkLedger.transfer}) is
 * used whenever no external provider is present.
 */
public final class WaymarkTransfer {

    private WaymarkTransfer() {
    }

    /** Provider-verified money movement for one party. */
    public interface Provider {
        boolean has(UUID holder, long amount);

        boolean withdraw(UUID holder, long amount);

        boolean deposit(UUID holder, long amount);
    }

    public enum Status {
        OK,
        SELF_PAYMENT,
        INVALID_TARGET,
        INVALID_AMOUNT,
        PROVIDER_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
        WITHDRAW_FAILED,
        DEPOSIT_FAILED_REFUNDED,
        DEPOSIT_FAILED_REFUND_FAILED
    }

    public record Outcome(Status status, String reference) {
        public boolean sent() {
            return status == Status.OK;
        }
    }

    public static Outcome external(UUID from, UUID to, long amount, Provider provider) {
        if (from == null || to == null) return new Outcome(Status.INVALID_TARGET, null);
        if (from.equals(to)) return new Outcome(Status.SELF_PAYMENT, null);
        if (amount <= 0) return new Outcome(Status.INVALID_AMOUNT, null);
        final String reference = "pay from=" + from + " to=" + to + " amount=" + amount;
        if (provider == null) return new Outcome(Status.PROVIDER_UNAVAILABLE, reference);
        if (!provider.has(from, amount)) return new Outcome(Status.INSUFFICIENT_FUNDS, reference);
        if (!provider.withdraw(from, amount)) return new Outcome(Status.WITHDRAW_FAILED, reference);
        if (provider.deposit(to, amount)) return new Outcome(Status.OK, reference);
        boolean recovered = provider.withdraw(to, amount);
        return new Outcome(recovered ? Status.DEPOSIT_FAILED_REFUNDED : Status.DEPOSIT_FAILED_REFUND_FAILED,
                reference);
    }

    /** Admin credit; confirms the provider's response instead of assuming success. */
    public static boolean adminGive(Provider provider, UUID target, long amount) {
        return provider != null && target != null && amount > 0 && provider.deposit(target, amount);
    }

    /** Admin debit; refuses without sufficient funds. */
    public static boolean adminTake(Provider provider, UUID target, long amount) {
        return provider != null && target != null && amount > 0
                && provider.has(target, amount) && provider.withdraw(target, amount);
    }

    /** Admin absolute set; every adjustment leg is confirmed. */
    public static boolean adminSet(Provider provider, UUID target, long desired,
                                   ToLongFunction<UUID> balance) {
        if (provider == null || target == null || desired < 0) return false;
        long current = balance.applyAsLong(target);
        if (current > desired) return provider.withdraw(target, current - desired);
        if (current < desired) return provider.deposit(target, desired - current);
        return true;
    }
}
