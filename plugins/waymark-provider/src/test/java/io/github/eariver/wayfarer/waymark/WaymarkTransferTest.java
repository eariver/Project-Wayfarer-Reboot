package io.github.eariver.wayfarer.waymark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M3 acceptance tests for the external (Vault) transfer path: every economy response
 * is verified (tri-state), a refused credit refunds the SENDER, and no success message
 * can be produced by an unconfirmed leg.
 */
class WaymarkTransferTest {

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    /**
     * Fake provider: every leg can be forced to fail or to throw, and the balances of
     * both parties are tracked so the compensation target is observable.
     */
    final class FakeProvider implements WaymarkTransfer.Provider {
        boolean fundsOk = true;
        boolean withdrawOk = true;
        boolean depositOk = true;
        boolean withdrawThrows;
        boolean depositThrows;
        boolean hasThrows;
        long fromBalance = 1000L;
        long toBalance = 500L;

        @Override
        public boolean has(UUID holder, long amount) {
            if (hasThrows) throw new IllegalStateException("provider exploded");
            return fundsOk && balanceOf(holder) >= amount;
        }

        @Override
        public WaymarkTransfer.Confirmation withdraw(UUID holder, long amount) {
            if (withdrawThrows) throw new IllegalStateException("provider exploded");
            if (!withdrawOk) return WaymarkTransfer.Confirmation.FAILED;
            if (balanceOf(holder) < amount) return WaymarkTransfer.Confirmation.FAILED;
            adjust(holder, -amount);
            return WaymarkTransfer.Confirmation.OK;
        }

        @Override
        public WaymarkTransfer.Confirmation deposit(UUID holder, long amount) {
            if (depositThrows) throw new IllegalStateException("provider exploded");
            if (!depositOk) return WaymarkTransfer.Confirmation.FAILED;
            adjust(holder, amount);
            return WaymarkTransfer.Confirmation.OK;
        }

        private long balanceOf(UUID holder) {
            return holder.equals(from) ? fromBalance : toBalance;
        }

        private void adjust(UUID holder, long delta) {
            if (holder.equals(from)) fromBalance += delta;
            else toBalance += delta;
        }
    }

    private long total(FakeProvider p) {
        return p.fromBalance + p.toBalance;
    }

    @Test
    @DisplayName("normal transfer confirms both legs")
    void normalTransfer() {
        FakeProvider p = new FakeProvider();
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, p);
        assertEquals(WaymarkTransfer.Status.OK, out.status());
        assertTrue(out.sent());
        assertFalse(out.needsReconciliation());
        assertEquals(750L, p.fromBalance);
        assertEquals(750L, p.toBalance);
    }

    @Test
    @DisplayName("self-payment is rejected")
    void selfPayment() {
        FakeProvider p = new FakeProvider();
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, from, 10L, p);
        assertEquals(WaymarkTransfer.Status.SELF_PAYMENT, out.status());
        assertEquals(1000L, p.fromBalance);
    }

    @Test
    @DisplayName("invalid amount is rejected")
    void invalidAmount() {
        FakeProvider p = new FakeProvider();
        assertEquals(WaymarkTransfer.Status.INVALID_AMOUNT,
                WaymarkTransfer.external(from, to, 0L, p).status());
        assertEquals(WaymarkTransfer.Status.INVALID_AMOUNT,
                WaymarkTransfer.external(from, to, -5L, p).status());
    }

    @Test
    @DisplayName("missing provider is reported, never a silent fallback")
    void providerMissing() {
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 10L, null);
        assertEquals(WaymarkTransfer.Status.PROVIDER_UNAVAILABLE, out.status());
    }

    @Test
    @DisplayName("insufficient funds never debits")
    void insufficientFunds() {
        FakeProvider p = new FakeProvider();
        p.fromBalance = 5L;
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 10L, p);
        assertEquals(WaymarkTransfer.Status.INSUFFICIENT_FUNDS, out.status());
        assertEquals(5L, p.fromBalance);
        assertEquals(500L, p.toBalance);
    }

    @Test
    @DisplayName("unconfirmed withdraw never credits the recipient")
    void withdrawUnconfirmed() {
        FakeProvider p = new FakeProvider();
        p.withdrawOk = false;
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, p);
        assertEquals(WaymarkTransfer.Status.WITHDRAW_FAILED, out.status());
        assertEquals(1000L, p.fromBalance);
        assertEquals(500L, p.toBalance);
        assertFalse(out.needsReconciliation());
    }

    @Test
    @DisplayName("throwing withdraw is UNKNOWN, never reported as a clean failure")
    void withdrawThrows() {
        FakeProvider p = new FakeProvider();
        p.withdrawThrows = true;
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, p);
        assertEquals(WaymarkTransfer.Status.WITHDRAW_UNKNOWN, out.status());
        assertTrue(out.needsReconciliation());
        assertEquals(1000L, p.fromBalance, "no further leg may run while the debit is unknown");
        assertEquals(500L, p.toBalance);
        assertNotNull(out.reference());
    }

    /**
     * Scripted provider: the debit succeeds, the credit is refused, and the refund leg's
     * answer is whatever the test decides. This is the exact R-01 scenario.
     */
    private final class ScriptedProvider implements WaymarkTransfer.Provider {
        WaymarkTransfer.Confirmation refund = WaymarkTransfer.Confirmation.OK;
        boolean refundThrows;
        long fromBalance = 1000L;
        long toBalance = 500L;
        int deposits;

        @Override
        public boolean has(UUID holder, long amount) {
            return (holder.equals(from) ? fromBalance : toBalance) >= amount;
        }

        @Override
        public WaymarkTransfer.Confirmation withdraw(UUID holder, long amount) {
            fromBalance -= amount;
            return WaymarkTransfer.Confirmation.OK;
        }

        @Override
        public WaymarkTransfer.Confirmation deposit(UUID holder, long amount) {
            deposits++;
            if (deposits == 1) return WaymarkTransfer.Confirmation.FAILED; // credit refused
            if (refundThrows) throw new IllegalStateException("refund path exploded");
            if (refund == WaymarkTransfer.Confirmation.OK) fromBalance += amount;
            return refund;
        }
    }

    @Test
    @DisplayName("R-01: a refused deposit refunds the SENDER and never touches the recipient")
    void refusedDepositRefundsSender() {
        ScriptedProvider p = new ScriptedProvider();
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, p);
        assertEquals(WaymarkTransfer.Status.DEPOSIT_FAILED_REFUNDED, out.status());
        assertFalse(out.sent());
        // A = 1000, B = 500 restored exactly; the recipient's own balance is untouched
        assertEquals(1000L, p.fromBalance);
        assertEquals(500L, p.toBalance);
        assertEquals(1500L, p.fromBalance + p.toBalance, "total currency is conserved");
    }

    @Test
    @DisplayName("R-01: a refused deposit with an unconfirmed refund is escalated")
    void refusedDepositRefundUnconfirmed() {
        ScriptedProvider p = new ScriptedProvider();
        p.refund = WaymarkTransfer.Confirmation.FAILED;
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, p);
        assertEquals(WaymarkTransfer.Status.DEPOSIT_FAILED_REFUND_FAILED, out.status());
        assertTrue(out.needsReconciliation());
        assertEquals(750L, p.fromBalance, "the debit stands until an admin refunds");
        assertEquals(500L, p.toBalance, "the recipient is never used as a compensation source");
        assertEquals(1250L, p.fromBalance + p.toBalance);
    }

    @Test
    @DisplayName("R-01: an unknown deposit outcome attempts no compensation at all")
    void unknownDepositNoCompensation() {
        FakeProvider p = new FakeProvider();
        p.depositThrows = true;
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, p);
        assertEquals(WaymarkTransfer.Status.DEPOSIT_UNKNOWN, out.status());
        assertTrue(out.needsReconciliation());
        assertEquals(750L, p.fromBalance);
        assertEquals(500L, p.toBalance, "no blind second deposit: the deposit may have landed");
    }

    @Test
    @DisplayName("R-01: a throwing refund is UNKNOWN and reported for reconciliation")
    void throwingRefundIsUnknown() {
        FakeProvider p = new FakeProvider();
        p.depositOk = false;
        // make only the first deposit (to `to`) fail and the second (refund of `from`)
        // throw: use a counter-driven fake
        final int[] deposits = {0};
        WaymarkTransfer.Provider flaky = new WaymarkTransfer.Provider() {
            @Override
            public boolean has(UUID holder, long amount) {
                return true;
            }

            @Override
            public WaymarkTransfer.Confirmation withdraw(UUID holder, long amount) {
                return WaymarkTransfer.Confirmation.OK;
            }

            @Override
            public WaymarkTransfer.Confirmation deposit(UUID holder, long amount) {
                deposits[0]++;
                if (deposits[0] == 1) return WaymarkTransfer.Confirmation.FAILED;
                throw new IllegalStateException("refund path exploded");
            }
        };
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, flaky);
        assertEquals(WaymarkTransfer.Status.REFUND_UNKNOWN, out.status());
        assertTrue(out.needsReconciliation());
        assertEquals(2, deposits[0], "the refund leg itself must have been attempted");
    }

    @Test
    @DisplayName("a throwing balance query is treated as unaffordable (nothing moves)")
    void throwingBalanceQuery() {
        FakeProvider p = new FakeProvider();
        p.hasThrows = true;
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, p);
        assertEquals(WaymarkTransfer.Status.INSUFFICIENT_FUNDS, out.status());
        assertEquals(1000L, p.fromBalance);
        assertEquals(500L, p.toBalance);
    }

    @Test
    @DisplayName("admin give/take/set confirm their results")
    void adminOps() {
        FakeProvider p = new FakeProvider();
        assertTrue(WaymarkTransfer.adminGive(p, to, 100L));
        assertEquals(600L, p.toBalance);
        assertTrue(WaymarkTransfer.adminTake(p, to, 40L));
        assertEquals(560L, p.toBalance);
        assertFalse(WaymarkTransfer.adminTake(p, to, 1000L));
        assertFalse(WaymarkTransfer.adminGive(p, to, 0L));
        assertTrue(WaymarkTransfer.adminSet(p, to, 500L, u -> p.toBalance));
        assertEquals(500L, p.toBalance);
        assertTrue(WaymarkTransfer.adminSet(p, to, 200L, u -> p.toBalance));
        assertEquals(200L, p.toBalance);
        assertFalse(WaymarkTransfer.adminSet(p, to, -1L, u -> p.toBalance));
    }

    @Test
    @DisplayName("admin debit refuses without funds and never debits")
    void adminTakeInsufficient() {
        FakeProvider p = new FakeProvider();
        p.toBalance = 10L;
        assertFalse(WaymarkTransfer.adminTake(p, to, 50L));
        assertEquals(10L, p.toBalance);
    }

    @Test
    @DisplayName("admin deposit failure is reported as not done")
    void adminGiveFails() {
        FakeProvider p = new FakeProvider();
        p.depositOk = false;
        assertFalse(WaymarkTransfer.adminGive(p, to, 10L));
        assertEquals(500L, p.toBalance);
    }

    @Test
    @DisplayName("repeated operations carry distinct references")
    void referencesAreUnique() {
        String a = WaymarkTransfer.newReference(from, to, 250L);
        String b = WaymarkTransfer.newReference(from, to, 250L);
        assertNotEquals(a, b);
        assertTrue(a.startsWith("pay-"));
    }
}
