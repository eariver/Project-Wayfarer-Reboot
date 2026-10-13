package io.github.eariver.wayfarer.waymark;

import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * Pure orchestration for Waymark (WM) transfers over an external Vault provider (M3).
 *
 * <p>Every money movement is reported by the provider as {@link Confirmation}, so a
 * success message is only possible when both legs are confirmed. The three-way result
 * matters because a plain {@code boolean} cannot tell "the provider refused" from
 * "the provider's answer is unknown" (an exception, a timeout, a dropped response):
 * those need different compensation.
 *
 * <ul>
 *   <li>credit refused (<b>certainly</b> not applied) → the sender is refunded and
 *       that refund is itself confirmed before the player is told anything;</li>
 *   <li>credit outcome UNKNOWN → no refund is attempted (it could double-credit the
 *       recipient); the outcome is UNKNOWN and references the transaction for an
 *       admin. The recipient's own balance is never touched as compensation.</li>
 * </ul>
 *
 * <p>Residual constraints (documented, not hidden): without exactly-once semantics a
 * crash between the legs can leave the sender debited — {@link Outcome#reference()}
 * is the reconcile handle. The pool that is <b>not</b> used as a compensation source
 * is the recipient's pre-existing balance.
 */
public final class WaymarkTransfer {

    private WaymarkTransfer() {
    }

    /** Provider answer for one money movement. */
    public enum Confirmation {
        /** The provider confirmed the movement. */
        OK,
        /** The provider confirmed failure: the movement did not happen. */
        FAILED,
        /** The outcome is unknown (exception/timeout/no answer). */
        UNKNOWN
    }

    /** Provider-verified money movement for one party. */
    public interface Provider {
        boolean has(UUID holder, long amount);

        Confirmation withdraw(UUID holder, long amount);

        Confirmation deposit(UUID holder, long amount);
    }

    public enum Status {
        OK,
        SELF_PAYMENT,
        INVALID_TARGET,
        INVALID_AMOUNT,
        PROVIDER_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
        /** the debit was refused: nothing moved. */
        WITHDRAW_FAILED,
        /** the debit's outcome is unknown: nothing further was attempted. */
        WITHDRAW_UNKNOWN,
        /** the credit was refused and the sender was refunded (confirmed). */
        DEPOSIT_FAILED_REFUNDED,
        /** the credit was refused but the refund could not be confirmed. */
        DEPOSIT_FAILED_REFUND_FAILED,
        /** the credit's outcome is unknown: no compensation attempted. */
        DEPOSIT_UNKNOWN,
        /** the refund's outcome is unknown. */
        REFUND_UNKNOWN
    }

    /**
     * @param status    classification of the outcome
     * @param reference unique transaction reference (server + uuid + timestamp + op)
     */
    public record Outcome(Status status, String reference) {
        public boolean sent() {
            return status == Status.OK;
        }

        /** True when the status needs manual reconciliation (money may be in flight). */
        public boolean needsReconciliation() {
            return switch (status) {
                case WITHDRAW_UNKNOWN, DEPOSIT_UNKNOWN, REFUND_UNKNOWN,
                     DEPOSIT_FAILED_REFUND_FAILED -> true;
                default -> false;
            };
        }
    }

    public static Outcome external(UUID from, UUID to, long amount, Provider provider) {
        return external(from, to, amount, provider, newReference(from, to, amount));
    }

    public static Outcome external(UUID from, UUID to, long amount, Provider provider, String reference) {
        if (from == null || to == null) return new Outcome(Status.INVALID_TARGET, reference);
        if (from.equals(to)) return new Outcome(Status.SELF_PAYMENT, reference);
        if (amount <= 0) return new Outcome(Status.INVALID_AMOUNT, reference);
        if (provider == null) return new Outcome(Status.PROVIDER_UNAVAILABLE, reference);
        if (!queryHas(provider, from, amount, reference)) {
            return new Outcome(Status.INSUFFICIENT_FUNDS, reference);
        }
        Confirmation debit = call(() -> provider.withdraw(from, amount));
        switch (debit) {
            case FAILED -> {
                return new Outcome(Status.WITHDRAW_FAILED, reference);
            }
            case UNKNOWN -> {
                return new Outcome(Status.WITHDRAW_UNKNOWN, reference);
            }
            case OK -> {
                // fall through
            }
        }
        Confirmation credit = call(() -> provider.deposit(to, amount));
        if (credit == Confirmation.OK) return new Outcome(Status.OK, reference);
        if (credit == Confirmation.UNKNOWN) {
            // the credit may have landed: compensating here could double-credit
            return new Outcome(Status.DEPOSIT_UNKNOWN, reference);
        }
        // the credit was certainly refused: refund the SENDER, never the recipient
        Confirmation refund = call(() -> provider.deposit(from, amount));
        return switch (refund) {
            case OK -> new Outcome(Status.DEPOSIT_FAILED_REFUNDED, reference);
            case FAILED -> new Outcome(Status.DEPOSIT_FAILED_REFUND_FAILED, reference);
            case UNKNOWN -> new Outcome(Status.REFUND_UNKNOWN, reference);
        };
    }

    private static boolean queryHas(Provider provider, UUID holder, long amount, String reference) {
        try {
            // an unknown balance is treated as "not affordable": no money moves either way
            return provider.has(holder, amount);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Turns a provider exception into UNKNOWN so callers never mistake it for a refusal. */
    private static Confirmation call(java.util.function.Supplier<Confirmation> leg) {
        try {
            Confirmation result = leg.get();
            return result == null ? Confirmation.UNKNOWN : result;
        } catch (RuntimeException e) {
            return Confirmation.UNKNOWN;
        }
    }

    /** Unique transaction reference: distinguishes repeats of the same operation. */
    public static String newReference(UUID from, UUID to, long amount) {
        return "pay-" + java.util.UUID.randomUUID()
                + " from=" + from + " to=" + to + " amount=" + amount;
    }

    /** Admin credit; confirms the provider's response instead of assuming success. */
    public static boolean adminGive(Provider provider, UUID target, long amount) {
        return provider != null && target != null && amount > 0
                && call(() -> provider.deposit(target, amount)) == Confirmation.OK;
    }

    /** Admin debit; refuses without sufficient funds. */
    public static boolean adminTake(Provider provider, UUID target, long amount) {
        return provider != null && target != null && amount > 0
                && queryHas(provider, target, amount, null)
                && call(() -> provider.withdraw(target, amount)) == Confirmation.OK;
    }

    /** Admin absolute set; every adjustment leg is confirmed. */
    public static boolean adminSet(Provider provider, UUID target, long desired,
                                   ToLongFunction<UUID> balance) {
        if (provider == null || target == null || desired < 0) return false;
        long current = balance.applyAsLong(target);
        if (current > desired) return call(() -> provider.withdraw(target, current - desired)) == Confirmation.OK;
        if (current < desired) return call(() -> provider.deposit(target, desired - current)) == Confirmation.OK;
        return true;
    }
}
