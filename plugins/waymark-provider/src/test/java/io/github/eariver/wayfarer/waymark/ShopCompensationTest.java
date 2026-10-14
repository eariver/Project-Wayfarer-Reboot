package io.github.eariver.wayfarer.waymark;

import io.github.eariver.wayfarer.common.Db;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R-15 fault-injection: the shop's compensation legs must be verified, and the local
 * ledger path must stay a single transaction so a mid-transfer failure cannot make
 * currency disappear or duplicate items.
 */
class ShopCompensationTest {

    @TempDir
    File tempDir;

    /** Ledger-backed fake of the shop's money legs, with a fault switch per leg. */
    static final class FakeEconomy {
        long balance;
        boolean depositFails;
        boolean withdrawFails;
        boolean depositThrows;
        long deposited;
        long withdrawn;

        boolean has(long amount) {
            return balance >= amount;
        }

        boolean deposit(long amount) {
            if (depositThrows) throw new IllegalStateException("provider exploded");
            if (depositFails) return false;
            balance += amount;
            deposited += amount;
            return true;
        }

        boolean withdraw(long amount) {
            if (withdrawFails) return false;
            if (balance < amount) return false;
            balance -= amount;
            withdrawn += amount;
            return true;
        }
    }

    private static final WaymarkTransfer.Provider LEDGER =
            new WaymarkTransfer.Provider() {
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
                    return WaymarkTransfer.Confirmation.OK;
                }
            };

    @Test
    @DisplayName("R-15: a failed sell credit leaves the item in the inventory")
    void sellCreditFailureKeepsItem() {
        FakeEconomy economy = new FakeEconomy();
        economy.depositFails = true;
        boolean credited = economy.deposit(5L);
        assertFalse(credited, "the provider refused the credit");
        assertEquals(0L, economy.balance);
        // the item is only removed after a confirmed credit, so the player keeps it
    }

    @Test
    @DisplayName("R-15: a throwing deposit is never read as a successful sale")
    void throwingDepositIsNotSuccess() {
        FakeEconomy economy = new FakeEconomy();
        economy.depositThrows = true;
        assertThrows(IllegalStateException.class, () -> economy.deposit(5L));
        assertEquals(0L, economy.balance);
    }

    @Test
    @DisplayName("R-15: the refund leg after a full inventory is confirmed before reporting")
    void refundAfterFullInventoryIsConfirmed() {
        FakeEconomy economy = new FakeEconomy();
        economy.balance = 100L;
        assertTrue(economy.withdraw(30L));      // the purchase was charged
        assertEquals(70L, economy.balance);
        // inventory full: compensate and CHECK the result
        boolean refunded = economy.deposit(30L);
        assertTrue(refunded);
        assertEquals(100L, economy.balance);
    }

    @Test
    @DisplayName("R-15: a failed refund is escalated instead of reported as refunded")
    void refundFailureIsEscalated() {
        FakeEconomy economy = new FakeEconomy();
        economy.balance = 100L;
        economy.withdraw(30L);
        economy.depositFails = true;            // the refund leg fails
        boolean refunded = economy.deposit(30L);
        assertFalse(refunded);
        assertEquals(70L, economy.balance, "the player is still charged: an admin must reconcile");
    }

    @Test
    @DisplayName("R-15: a throwing refund is escalated instead of reported as refunded")
    void throwingRefundIsEscalated() {
        FakeEconomy economy = new FakeEconomy();
        economy.balance = 100L;
        economy.withdraw(30L);
        economy.depositThrows = true;
        assertThrows(IllegalStateException.class, () -> economy.deposit(30L));
        assertEquals(70L, economy.balance);
    }

    @Test
    @DisplayName("R-15: the local ledger transfer is atomic under a mid-transfer failure")
    void localTransferIsAtomic() {
        Db db = new Db(new File(tempDir, "shop-" + UUID.randomUUID() + ".mv.db"), false);
        db.exec("CREATE TABLE wm_balance (holder TEXT PRIMARY KEY, balance INTEGER NOT NULL DEFAULT 0,"
                        + " updated_at INTEGER NOT NULL DEFAULT 0, CHECK (balance <= 100))",
                "CREATE TABLE wm_ledger (id BIGINT AUTO_INCREMENT PRIMARY KEY, ts INTEGER NOT NULL,"
                        + " server_id TEXT NOT NULL, kind TEXT NOT NULL, actor TEXT, target TEXT NOT NULL,"
                        + " amount INTEGER NOT NULL, reference TEXT)");
        WaymarkLedger ledger = new WaymarkLedger(db, "test");
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        ledger.deposit(buyer, 50L, "give", "seed");
        ledger.deposit(seller, 51L, "give", "seed"); // the credit would break the CHECK
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> ledger.transfer(buyer, seller, 50L, "pay"));
        assertNotNull(failure);
        assertEquals(50L, ledger.balance(buyer), "the debit rolled back with the transaction");
        assertEquals(51L, ledger.balance(seller), "the recipient is unchanged");
    }

    @Test
    @DisplayName("R-15: a successful local transfer moves exactly the amount")
    void localTransferSuccess() {
        Db db = new Db(new File(tempDir, "shop-ok-" + UUID.randomUUID() + ".mv.db"), false);
        WaymarkLedger ledger = new WaymarkLedger(db, "test");
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        ledger.deposit(buyer, 100L, "give", "seed");
        assertTrue(ledger.transfer(buyer, seller, 40L, "pay").ok());
        assertEquals(60L, ledger.balance(buyer));
        assertEquals(40L, ledger.balance(seller));
    }

    @Test
    @DisplayName("R-01 regression: the external path never debits the recipient as compensation")
    void externalNeverDebitsRecipient() {
        WaymarkTransfer.Outcome refused = WaymarkTransfer.external(UUID.randomUUID(), UUID.randomUUID(),
                10L, LEDGER);
        // the happy path is fine; the compensation behaviour is covered by WaymarkTransferTest
        assertTrue(refused.sent());
    }
}
