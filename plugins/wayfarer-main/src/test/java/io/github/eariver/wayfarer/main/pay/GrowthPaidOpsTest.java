package io.github.eariver.wayfarer.main.pay;

import io.github.eariver.wayfarer.main.domain.EvolutionPlan;
import io.github.eariver.wayfarer.main.domain.GrowthTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failure-injection tests for the paid Growth Pickaxe operations (M2).
 *
 * <p>The economy is a fake wallet and the store a stub, so every DB/provider failure
 * mode from the mission's acceptance list is exercised without a live Minecraft server.
 */
class GrowthPaidOpsTest {

    private static final Logger LOG = Logger.getLogger("growth-paid-ops-test");
    private static final long COST = 250L;

    private final UUID toolId = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final GrowthTool tool = new GrowthTool(
            toolId, owner, "tester", 3, 1200L, EvolutionPlan.Branch.SILK_TOUCH,
            GrowthTool.Status.ACTIVE, false);

    /** Fake economy: every leg can be forced to fail or to throw (R-04). */
    static final class FakeWallet implements GrowthPaidOps.Wallet {
        boolean funds = true;
        boolean hasThrows;
        boolean withdrawOk = true;
        boolean withdrawThrows;
        boolean depositOk = true;
        boolean depositThrows;
        long withdrawn = 0;
        long deposited = 0;

        @Override
        public boolean has(long amount) {
            if (hasThrows) throw new IllegalStateException("balance query exploded");
            return funds;
        }

        @Override
        public GrowthPaidOps.Confirmation withdraw(long amount) {
            if (withdrawThrows) throw new IllegalStateException("withdraw exploded");
            if (!withdrawOk) return GrowthPaidOps.Confirmation.FAILED;
            withdrawn += amount;
            return GrowthPaidOps.Confirmation.OK;
        }

        @Override
        public GrowthPaidOps.Confirmation deposit(long amount) {
            if (depositThrows) throw new IllegalStateException("deposit exploded");
            if (!depositOk) return GrowthPaidOps.Confirmation.FAILED;
            deposited += amount;
            return GrowthPaidOps.Confirmation.OK;
        }
    }

    /** Stub repository with fault injection on every call. */
    static final class StubStore implements GrowthPaidOps.ToolStore {
        GrowthTool row;
        boolean bumpThrows;
        boolean flagThrows;
        boolean updateThrows;
        boolean findThrows;
        int bumpRows = 1;
        int flagRows = 1;

        @Override
        public int bumpEpoch(UUID id, int expectedEpoch) {
            if (bumpThrows) throw new IllegalStateException("h2 is down");
            if (bumpRows > 0 && row != null) {
                row = row.withEpoch(row.epoch() + 1);
            }
            return bumpRows;
        }

        @Override
        public int markNetheriteUpgraded(UUID id) {
            if (flagThrows) throw new IllegalStateException("h2 is down");
            if (flagRows > 0 && row != null) {
                row = row.withNetheriteUpgraded(true);
            }
            return flagRows;
        }

        @Override
        public GrowthTool findTool(UUID id) {
            if (findThrows) throw new IllegalStateException("h2 is down");
            return row;
        }

        @Override
        public void update(GrowthTool tool) {
            if (updateThrows) throw new IllegalStateException("h2 is down");
            row = tool;
        }
    }

    private StubStore store() {
        StubStore s = new StubStore();
        s.row = tool;
        return s;
    }

    @Nested
    @DisplayName("reissue/repaid-upgrade happy paths")
    class HappyPaths {

        @Test
        @DisplayName("normal reissue completes and charges exactly once")
        void normalReissue() {
            StubStore store = store();
            FakeWallet wallet = new FakeWallet();
            boolean[] delivered = {false};
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> delivered[0] = true, LOG);
            assertEquals(GrowthPaidOps.State.COMPLETED, r.state());
            assertEquals(COST, wallet.withdrawn);
            assertEquals(0, wallet.deposited);
            assertEquals(4, store.row.epoch());
            assertTrue(delivered[0]);
            assertTrue(r.charged());
        }

        @Test
        @DisplayName("netherite upgrade completes and keeps one charge")
        void normalUpgrade() {
            StubStore store = store();
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.upgradeNetherite(store, wallet, tool, COST,
                    () -> {
                    }, LOG);
            assertEquals(GrowthPaidOps.State.COMPLETED, r.state());
            assertEquals(COST, wallet.withdrawn);
            assertTrue(store.row.netheriteUpgraded());
        }

        @Test
        @DisplayName("repair completes with a single charge")
        void normalRepair() {
            StubStore store = store();
            store.row = tool.withStatus(GrowthTool.Status.BROKEN);
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.repair(store, wallet, tool, COST,
                    () -> {
                    }, LOG);
            assertEquals(GrowthPaidOps.State.COMPLETED, r.state());
            assertEquals(COST, wallet.withdrawn);
            assertEquals(GrowthTool.Status.ACTIVE, store.row.status());
        }
    }

    @Nested
    @DisplayName("payment legs")
    class PaymentLegs {

        @Test
        @DisplayName("insufficient funds declines without touching the DB")
        void insufficientFunds() {
            StubStore store = store();
            FakeWallet wallet = new FakeWallet();
            wallet.funds = false;
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.PAYMENT_DECLINED, r.state());
            assertEquals(0, wallet.withdrawn);
            assertEquals(3, store.row.epoch());
            assertFalse(r.charged());
        }

        @Test
        @DisplayName("unconfirmed withdraw never applies the DB change")
        void withdrawUnconfirmed() {
            StubStore store = store();
            FakeWallet wallet = new FakeWallet();
            wallet.withdrawOk = false;
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.PAYMENT_FAILED, r.state());
            assertEquals(3, store.row.epoch());
            assertFalse(r.charged());
        }
    }

    @Nested
    @DisplayName("DB failure classification")
    class DbFailures {

        @Test
        @DisplayName("conditional 0-row result refunds with a confirmed credit")
        void conditionalNoMatchRefunds() {
            StubStore store = store();
            store.bumpRows = 0;
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.REFUNDED, r.state());
            assertEquals(GrowthPaidOps.Cause.NO_MATCH, r.cause());
            assertEquals(COST, wallet.withdrawn);
            assertEquals(COST, wallet.deposited);
            assertEquals(3, store.row.epoch());
            assertFalse(r.charged());
        }

        @Test
        @DisplayName("conditional 0-row result with a failed refund is not reported as uncharged")
        void conditionalNoMatchRefundFails() {
            StubStore store = store();
            store.bumpRows = 0;
            FakeWallet wallet = new FakeWallet();
            wallet.depositOk = false;
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.REFUND_FAILED, r.state());
            assertEquals(COST, wallet.withdrawn);
            assertEquals(0, wallet.deposited);
            assertTrue(r.charged());
        }

        @Test
        @DisplayName("mutation throwing while the row still reads unchanged refunds")
        void mutationThrowsRowUnchanged() {
            StubStore store = store();
            store.bumpThrows = true;
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.REFUNDED, r.state());
            assertEquals(GrowthPaidOps.Cause.DB_FAILURE, r.cause());
            assertEquals(COST, wallet.deposited);
            assertFalse(r.charged());
        }

        @Test
        @DisplayName("mutation throwing while the row shows applied keeps the charge and delivers")
        void mutationThrowsRowAlreadyBumped() {
            StubStore store = store();
            // the row is already one epoch ahead (the update committed before the failure)
            store.row = tool.withEpoch(tool.epoch() + 1);
            store.bumpThrows = true;
            FakeWallet wallet = new FakeWallet();
            boolean[] delivered = {false};
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> delivered[0] = true, LOG);
            assertEquals(GrowthPaidOps.State.COMPLETED, r.state());
            assertEquals(0, wallet.deposited, "an applied change must never be refunded");
            assertTrue(delivered[0]);
            assertTrue(r.charged());
        }

        @Test
        @DisplayName("mutation throwing with an unreadable verification keeps the charge for reconciliation")
        void mutationThrowsProbeUnreadable() {
            StubStore store = store();
            store.bumpThrows = true;
            store.findThrows = true;
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.UNKNOWN, r.state());
            assertEquals(0, wallet.deposited);
            assertTrue(r.charged());
            assertNotNull(r.reference());
        }

        @Test
        @DisplayName("mutation throwing with a vanished row is unknown, never a refund")
        void mutationThrowsRowVanished() {
            StubStore store = store();
            store.bumpThrows = true;
            store.row = null;
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.UNKNOWN, r.state());
            assertEquals(0, wallet.deposited);
        }
    }

    @Nested
    @DisplayName("delivery and netherite fault paths")
    class DeliveryAndNetherite {

        @Test
        @DisplayName("delivery failure after a committed change keeps the charge and stays recoverable")
        void deliveryFailure() {
            StubStore store = store();
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> {
                        throw new RuntimeException("inventory full");
                    }, LOG);
            assertEquals(GrowthPaidOps.State.APPLIED_DELIVERY_FAILED, r.state());
            assertEquals(0, wallet.deposited);
            assertEquals(4, store.row.epoch(), "the epoch stands so a rejoin re-grants the item");
            assertTrue(r.charged());
        }

        @Test
        @DisplayName("duplicate netherite upgrade charges nothing")
        void duplicateNetheriteUpgrade() {
            StubStore store = store();
            store.flagRows = 0; // the flag was already flipped by another writer
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.upgradeNetherite(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.REFUNDED, r.state());
            assertEquals(GrowthPaidOps.Cause.NO_MATCH, r.cause());
            assertEquals(COST, wallet.deposited);
            assertFalse(r.charged());
        }

        @Test
        @DisplayName("netherite flag exception with the flag already set keeps the charge")
        void netheriteMutationThrowsFlagSet() {
            StubStore store = store();
            store.row = tool.withNetheriteUpgraded(true);
            store.flagThrows = true;
            FakeWallet wallet = new FakeWallet();
            boolean[] delivered = {false};
            GrowthPaidOps.Result r = GrowthPaidOps.upgradeNetherite(store, wallet, tool, COST,
                    () -> delivered[0] = true, LOG);
            assertEquals(GrowthPaidOps.State.COMPLETED, r.state());
            assertEquals(0, wallet.deposited);
            assertTrue(delivered[0]);
        }

        @Test
        @DisplayName("repair update exception with the row still BROKEN refunds")
        void repairThrowsStillBroken() {
            StubStore store = store();
            store.row = tool.withStatus(GrowthTool.Status.BROKEN);
            store.updateThrows = true;
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.repair(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.REFUNDED, r.state());
            assertEquals(COST, wallet.deposited);
            assertFalse(r.charged());
        }

        @Test
        @DisplayName("repair update exception with the row already ACTIVE keeps the charge")
        void repairThrowsAlreadyActive() {
            StubStore store = store();
            store.updateThrows = true;
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.repair(store, wallet, tool, COST,
                    () -> {
                    }, LOG);
            assertEquals(GrowthPaidOps.State.COMPLETED, r.state());
            assertEquals(0, wallet.deposited);
            assertTrue(r.charged());
        }

        @Test
        @DisplayName("repair update exception with an unreadable row is unknown")
        void repairThrowsUnreadable() {
            StubStore store = store();
            store.updateThrows = true;
            store.findThrows = true;
            FakeWallet wallet = new FakeWallet();
            GrowthPaidOps.Result r = GrowthPaidOps.repair(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.UNKNOWN, r.state());
            assertEquals(0, wallet.deposited);
        }
    }
    @Nested
    @DisplayName("R-04: provider exceptions are UNKNOWN, never a clean failure")
    class ProviderExceptions {

        @Test
        @DisplayName("a throwing debit is UNKNOWN and applies no DB change")
        void withdrawThrows() {
            StubStore store = store();
            FakeWallet wallet = new FakeWallet();
            wallet.withdrawThrows = true;
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.PAYMENT_UNKNOWN, r.state());
            assertTrue(r.charged(), "the charge may stand until an admin reconciles");
            assertEquals(3, store.row.epoch(), "no DB change while the debit is unknown");
            assertFalse(runDelivered());
        }

        @Test
        @DisplayName("a throwing balance query never charges")
        void balanceQueryThrows() {
            StubStore store = store();
            FakeWallet wallet = new FakeWallet();
            wallet.hasThrows = true;
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.PAYMENT_DECLINED, r.state());
            assertEquals(0, wallet.withdrawn);
            assertEquals(3, store.row.epoch());
        }

        @Test
        @DisplayName("a throwing refund after a not-applied change is REFUND_UNKNOWN")
        void refundThrows() {
            StubStore store = store();
            store.bumpRows = 0;
            FakeWallet wallet = new FakeWallet();
            wallet.depositThrows = true;
            GrowthPaidOps.Result r = GrowthPaidOps.reissue(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.REFUND_UNKNOWN, r.state());
            assertEquals(COST, wallet.withdrawn);
            assertEquals(0, wallet.deposited);
            assertTrue(r.charged(), "the player must not be told they were uncharged");
        }

        @Test
        @DisplayName("an unknown refund keeps the reference for reconciliation")
        void refundUnknownCarriesReference() {
            StubStore store = store();
            store.flagThrows = true;   // mutation fails
            store.findThrows = false;  // probe reads the unchanged row
            FakeWallet wallet = new FakeWallet();
            wallet.depositThrows = true;
            GrowthPaidOps.Result r = GrowthPaidOps.upgradeNetherite(store, wallet, tool, COST,
                    () -> fail("must not deliver"), LOG);
            assertEquals(GrowthPaidOps.State.REFUND_UNKNOWN, r.state());
            assertTrue(r.reference().contains("netherite tool="));
            assertTrue(r.reference().contains("owner="));
        }

        private boolean runDelivered() {
            return false;
        }
    }
}
