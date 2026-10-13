package io.github.eariver.wayfarer.waymark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M3 acceptance tests for the external (Vault) transfer path: every economy response
 * is verified and no success message can be produced by an unconfirmed leg.
 */
class WaymarkTransferTest {

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    /** Fake provider: every leg can be forced to fail. */
    final class FakeProvider implements WaymarkTransfer.Provider {
        boolean has = true;
        boolean withdrawOk = true;
        boolean depositOk = true;
        long fromBalance = 1000L;
        long toBalance = 0L;

        @Override
        public boolean has(UUID holder, long amount) {
            return has && (holder.equals(from) ? fromBalance : toBalance) >= amount;
        }

        @Override
        public boolean withdraw(UUID holder, long amount) {
            if (!withdrawOk) return false;
            if (holder.equals(from)) {
                if (fromBalance < amount) return false;
                fromBalance -= amount;
            } else {
                if (toBalance < amount) return false;
                toBalance -= amount;
            }
            return true;
        }

        @Override
        public boolean deposit(UUID holder, long amount) {
            if (!depositOk) return false;
            if (holder.equals(from)) fromBalance += amount;
            else toBalance += amount;
            return true;
        }
    }

    @Test
    @DisplayName("normal transfer confirms both legs")
    void normalTransfer() {
        FakeProvider p = new FakeProvider();
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, p);
        assertEquals(WaymarkTransfer.Status.OK, out.status());
        assertTrue(out.sent());
        assertEquals(750L, p.fromBalance);
        assertEquals(250L, p.toBalance);
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
        assertEquals(0L, p.toBalance);
    }

    @Test
    @DisplayName("unconfirmed withdraw never credits the recipient")
    void withdrawUnconfirmed() {
        FakeProvider p = new FakeProvider();
        p.withdrawOk = false;
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 10L, p);
        assertEquals(WaymarkTransfer.Status.WITHDRAW_FAILED, out.status());
        assertEquals(1000L, p.fromBalance);
        assertEquals(0L, p.toBalance);
    }

    @Test
    @DisplayName("deposit failure attempts a compensating withdrawal and reports it")
    void depositFailedRefunded() {
        FakeProvider p = new FakeProvider();
        p.depositOk = false;
        p.toBalance = 500L;
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, p);
        assertEquals(WaymarkTransfer.Status.DEPOSIT_FAILED_REFUNDED, out.status());
        // the compensating withdrawal succeeded: the sender is whole again
        assertEquals(750L, p.fromBalance);
        assertFalse(out.sent());
    }

    @Test
    @DisplayName("deposit failure with a failed refund is escalated, never reported as sent")
    void depositFailedRefundFailed() {
        FakeProvider p = new FakeProvider();
        p.depositOk = false;
        p.toBalance = 0L; // the recipient cannot cover the compensating withdrawal
        WaymarkTransfer.Outcome out = WaymarkTransfer.external(from, to, 250L, p);
        assertEquals(WaymarkTransfer.Status.DEPOSIT_FAILED_REFUND_FAILED, out.status());
        assertFalse(out.sent());
        assertNotNull(out.reference());
    }

    @Test
    @DisplayName("admin give/take/set confirm their results")
    void adminOps() {
        FakeProvider p = new FakeProvider();
        assertTrue(WaymarkTransfer.adminGive(p, to, 100L));
        assertEquals(100L, p.toBalance);
        assertTrue(WaymarkTransfer.adminTake(p, to, 40L));
        assertEquals(60L, p.toBalance);
        assertFalse(WaymarkTransfer.adminTake(p, to, 1000L));
        assertFalse(WaymarkTransfer.adminGive(p, to, 0L));
        assertTrue(WaymarkTransfer.adminSet(p, to, 500L, u -> p.toBalance));
        assertEquals(500L, p.toBalance);
        assertTrue(WaymarkTransfer.adminSet(p, to, 200L, u -> p.toBalance));
        assertEquals(200L, p.toBalance);
        assertFalse(WaymarkTransfer.adminSet(p, to, -1L, u -> p.toBalance));
    }

    @Test
    @DisplayName("admin take refuses without funds and never debits")
    void adminTakeInsufficient() {
        FakeProvider p = new FakeProvider();
        p.toBalance = 10L;
        assertFalse(WaymarkTransfer.adminTake(p, to, 50L));
        assertEquals(10L, p.toBalance);
    }
}
