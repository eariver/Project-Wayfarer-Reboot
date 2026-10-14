package io.github.eariver.wayfarer.main.pay;

import io.github.eariver.wayfarer.main.domain.GrowthTool;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Compensating flow for the paid Growth Pickaxe operations (reissue, Netherite upgrade,
 * repair).
 *
 * <p>The Vault economy and the H2 store are <b>not</b> one ACID transaction, so these
 * operations deliberately do not claim exactly-once semantics. Instead every failure
 * path is classified through a re-read of the authoritative row:
 *
 * <ul>
 *   <li>{@code applied} (definitely or by probe) — the charge stands; a delivery failure
 *       is recovered on rejoin because the join flow re-grants the current epoch;</li>
 *   <li>{@code not applied} (conditional update affected 0 rows, or the probe reads the
 *       unchanged row) — a compensating refund is attempted and its own result verified;</li>
 *   <li>{@code unknown} (probe unreadable or the row vanished) — the charge is kept and a
 *       reference is surfaced to the player and the log so an admin can reconcile.</li>
 * </ul>
 *
 * <p>No player is ever told they were "not charged" unless the refund itself was
 * confirmed by the provider.
 */
public final class GrowthPaidOps {

    private GrowthPaidOps() {
    }

    /** Provider answer for one money movement. */
    public enum Confirmation {
        OK,
        FAILED,
        UNKNOWN
    }

    /** Provider-verified money movement; the Vault adapter is {@link VaultWallet}. */
    public interface Wallet {
        boolean has(long amount);

        /** Debit answer; UNKNOWN on exception or a missing response. */
        Confirmation withdraw(long amount);

        /** Credit answer (compensation leg); UNKNOWN on exception or a missing response. */
        Confirmation deposit(long amount);
    }

    /** Repository surface for the paid operations; {@code GrowthRepository} implements it. */
    public interface ToolStore {
        int bumpEpoch(UUID toolId, int expectedEpoch);

        int markNetheriteUpgraded(UUID toolId);

        /** Authoritative row, or {@code null} when absent. */
        GrowthTool findTool(UUID toolId);

        void update(GrowthTool tool);
    }

    public enum State {
        /** Paid change committed and the item was handed over. */
        COMPLETED,
        /** Paid change committed, but the item hand-over failed; rejoining recovers it. */
        APPLIED_DELIVERY_FAILED,
        /** Change did not apply and the refund was confirmed. */
        REFUNDED,
        /** Change did not apply but the refund failed: needs manual reconciliation. */
        REFUND_FAILED,
        /** Applied state could not be verified; the charge is kept for reconciliation. */
        UNKNOWN,
        /** The player could not afford the operation (nothing was charged). */
        PAYMENT_DECLINED,
        /** The provider refused the debit. */
        PAYMENT_FAILED,
        /** The debit's outcome is unknown: the charge may stand; needs reconciliation. */
        PAYMENT_UNKNOWN,
        /** The refund's outcome is unknown: the state may already be compensated. */
        REFUND_UNKNOWN
    }

    /** Why the conditional DB step did not apply. */
    public enum Cause {
        NONE,
        /** the conditional UPDATE matched 0 rows: another writer already applied the change */
        NO_MATCH,
        /** the UPDATE threw and the row reads back unchanged */
        DB_FAILURE
    }

    /**
     * @param state     classification of the outcome
     * @param reference stable string (tool/owner/cost) for admin reconciliation and logs
     * @param cause     why a refunded operation did not apply
     */
    public record Result(State state, String reference, Cause cause) {
        public Result(State state, String reference) {
            this(state, reference, Cause.NONE);
        }

        public boolean charged() {
            return switch (state) {
                case COMPLETED, APPLIED_DELIVERY_FAILED, UNKNOWN, REFUND_FAILED,
                     PAYMENT_UNKNOWN, REFUND_UNKNOWN -> true;
                case REFUNDED, PAYMENT_DECLINED, PAYMENT_FAILED -> false;
            };
        }
    }

    /** Side effect executed after the paid change is known to be applied. */
    public interface Delivery {
        void deliver() throws Exception;
    }

    /** Shared core: charge, apply, verify, compensate or keep charged. */
    public static Result run(Wallet wallet, long cost, String reference,
                             Supplier<Integer> mutation,
                             Supplier<Boolean> appliedProbe,
                             Delivery deliver,
                             Logger log) {
        boolean affordable;
        try {
            affordable = wallet.has(cost);
        } catch (RuntimeException e) {
            // an unknown balance is never treated as affordable: no money may move
            log.warning("[" + reference + "] balance query threw: " + e);
            return new Result(State.PAYMENT_DECLINED, reference);
        }
        if (!affordable) return new Result(State.PAYMENT_DECLINED, reference);
        Confirmation debit = call(() -> wallet.withdraw(cost), log, reference, "withdraw");
        if (debit != Confirmation.OK) {
            log.warning("[" + reference + "] debit " + debit + ": no DB change was attempted");
            return new Result(debit == Confirmation.UNKNOWN ? State.PAYMENT_UNKNOWN : State.PAYMENT_FAILED,
                    reference);
        }
        final int rows;
        try {
            rows = mutation.get();
        } catch (Exception e) {
            return classifyAfterMutationFailure(wallet, cost, reference, appliedProbe, deliver, log, e);
        }
        if (rows == 0) {
            // The conditional update matched nothing: our change definitely did not apply,
            // so the charge must be compensated.
            log.info("[" + reference + "] conditional update affected 0 rows");
            return compensate(wallet, cost, reference, State.REFUNDED, Cause.NO_MATCH, log);
        }
        return deliver(reference, deliver, log);
    }

    private static Result classifyAfterMutationFailure(Wallet wallet, long cost, String reference,
                                                       Supplier<Boolean> appliedProbe, Delivery deliver,
                                                       Logger log, Exception failure) {
        Boolean applied;
        try {
            applied = appliedProbe.get();
        } catch (Exception probeError) {
            applied = null;
            log.warning("[" + reference + "] could not verify the applied state after failure: " + probeError);
        }
        if (Boolean.TRUE.equals(applied)) {
            // The mutation committed despite the exception: the charge stands and the
            // hand-over must still finish.
            log.warning("[" + reference + "] paid mutation failed but the row shows applied; charge kept");
            return deliver(reference, deliver, log);
        }
        if (Boolean.FALSE.equals(applied)) {
            log.warning("[" + reference + "] paid mutation failed and the row shows unchanged; compensating");
            return compensate(wallet, cost, reference, State.REFUNDED, Cause.DB_FAILURE, log);
        }
        log.severe("[" + reference + "] applied state UNKNOWN after the paid mutation failed; "
                + "charge kept for reconciliation — do not report the player as uncharged");
        return new Result(State.UNKNOWN, reference);
    }

    private static Result compensate(Wallet wallet, long cost, String reference, State refundedState,
                                     Cause cause, Logger log) {
        Confirmation refund = call(() -> wallet.deposit(cost), log, reference, "refund");
        if (refund == Confirmation.OK) {
            log.info("[" + reference + "] refunded " + cost + " WM");
            return new Result(refundedState, reference, cause);
        }
        if (refund == Confirmation.UNKNOWN) {
            log.severe("[" + reference + "] REFUND UNKNOWN for " + cost + " WM — reconcile manually; "
                    + "the player must NOT be told they were not charged");
            return new Result(State.REFUND_UNKNOWN, reference, cause);
        }
        log.severe("[" + reference + "] REFUND FAILED for " + cost + " WM — reconcile manually; "
                + "the player must NOT be told they were not charged");
        return new Result(State.REFUND_FAILED, reference, cause);
    }

    /** R-04: turns a provider exception into UNKNOWN so it can never read as a refusal. */
    private static Confirmation call(java.util.function.Supplier<Confirmation> leg, Logger log,
                                     String reference, String what) {
        try {
            Confirmation result = leg.get();
            return result == null ? Confirmation.UNKNOWN : result;
        } catch (RuntimeException e) {
            log.warning("[" + reference + "] " + what + " threw, treated as UNKNOWN: " + e);
            return Confirmation.UNKNOWN;
        }
    }

    private static Result deliver(String reference, Delivery deliver, Logger log) {
        try {
            deliver.deliver();
            return new Result(State.COMPLETED, reference);
        } catch (Exception e) {
            // The paid change is committed; the join flow re-grants the current epoch so a
            // rejoin recovers the item.
            log.warning("[" + reference + "] item delivery failed after the paid change; "
                    + "the player recovers by rejoining: " + e);
            return new Result(State.APPLIED_DELIVERY_FAILED, reference);
        }
    }

    /** Conditional epoch bump; 0 rows means another writer already advanced the epoch. */
    public static Result reissue(ToolStore store, Wallet wallet, GrowthTool tool, long cost,
                                 Delivery deliver, Logger log) {
        int expected = tool.epoch();
        return run(wallet, cost, "reissue tool=" + tool.toolId() + " owner=" + tool.ownerUuid() + " cost=" + cost,
                () -> store.bumpEpoch(tool.toolId(), expected),
                () -> {
                    GrowthTool row = store.findTool(tool.toolId());
                    return row == null ? null : row.epoch() > expected;
                },
                deliver, log);
    }

    /** Conditional flag flip; 0 rows means another writer already unlocked the upgrade. */
    public static Result upgradeNetherite(ToolStore store, Wallet wallet, GrowthTool tool, long cost,
                                          Delivery deliver, Logger log) {
        return run(wallet, cost, "netherite tool=" + tool.toolId() + " owner=" + tool.ownerUuid() + " cost=" + cost,
                () -> store.markNetheriteUpgraded(tool.toolId()),
                () -> {
                    GrowthTool row = store.findTool(tool.toolId());
                    return row == null ? null : row.netheriteUpgraded();
                },
                deliver, log);
    }

    /** Unconditional status repair; the probe re-reads the authoritative status. */
    public static Result repair(ToolStore store, Wallet wallet, GrowthTool tool, long cost,
                                Delivery deliver, Logger log) {
        return run(wallet, cost, "repair tool=" + tool.toolId() + " owner=" + tool.ownerUuid() + " cost=" + cost,
                () -> {
                    store.update(tool.withStatus(GrowthTool.Status.ACTIVE));
                    return 1;
                },
                () -> {
                    GrowthTool row = store.findTool(tool.toolId());
                    return row == null ? null : row.status() == GrowthTool.Status.ACTIVE;
                },
                deliver, log);
    }
}
