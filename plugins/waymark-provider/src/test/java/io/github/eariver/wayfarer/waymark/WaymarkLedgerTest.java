package io.github.eariver.wayfarer.waymark;

import io.github.eariver.wayfarer.common.Db;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Local-ledger tests: transfers run as ONE transaction, so a failure in the middle can
 * never make currency disappear.
 */
class WaymarkLedgerTest {

    @TempDir
    File tempDir;

    private WaymarkLedger freshLedger() {
        Db db = new Db(new File(tempDir, "waymark-" + UUID.randomUUID() + ".mv.db"), false);
        return new WaymarkLedger(db, "test");
    }

    @Test
    @DisplayName("transfer moves both sides and the balances match")
    void transferBothSides() {
        WaymarkLedger ledger = freshLedger();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ledger.deposit(a, 500L, "give", "seed");
        WaymarkResult r = ledger.transfer(a, b, 200L, "pay");
        assertTrue(r.ok());
        assertEquals(300L, ledger.balance(a));
        assertEquals(200L, ledger.balance(b));
    }

    @Test
    @DisplayName("insufficient funds leaves both balances untouched")
    void transferInsufficient() {
        WaymarkLedger ledger = freshLedger();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ledger.deposit(a, 50L, "give", "seed");
        ledger.deposit(b, 10L, "give", "seed");
        WaymarkResult r = ledger.transfer(a, b, 100L, "pay");
        assertFalse(r.ok());
        assertEquals("insufficient funds", r.error());
        assertEquals(50L, ledger.balance(a));
        assertEquals(10L, ledger.balance(b));
    }

    @Test
    @DisplayName("self-transfer is rejected")
    void transferSelf() {
        WaymarkLedger ledger = freshLedger();
        UUID a = UUID.randomUUID();
        ledger.deposit(a, 100L, "give", "seed");
        WaymarkResult r = ledger.transfer(a, a, 10L, "pay");
        assertFalse(r.ok());
        assertEquals("cannot pay yourself", r.error());
        assertEquals(100L, ledger.balance(a));
    }

    @Test
    @DisplayName("a mid-transfer failure rolls back the debit (no currency loss)")
    void transferRollsBackOnCreditFailure() {
        Db db = new Db(new File(tempDir, "constrained-" + UUID.randomUUID() + ".mv.db"), false);
        // pre-create the tables with a CHECK the ledger's CREATE IF NOT EXISTS cannot replace,
        // so the credit leg fails while the debit leg is already applied
        db.exec("CREATE TABLE wm_balance (holder TEXT PRIMARY KEY, balance INTEGER NOT NULL DEFAULT 0,"
                        + " updated_at INTEGER NOT NULL DEFAULT 0, CHECK (balance <= 100))",
                "CREATE TABLE wm_ledger (id BIGINT AUTO_INCREMENT PRIMARY KEY, ts INTEGER NOT NULL,"
                        + " server_id TEXT NOT NULL, kind TEXT NOT NULL, actor TEXT, target TEXT NOT NULL,"
                        + " amount INTEGER NOT NULL, reference TEXT)");
        WaymarkLedger ledger = new WaymarkLedger(db, "test");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ledger.deposit(a, 50L, "give", "seed");
        ledger.deposit(b, 51L, "give", "seed"); // the credit would push b to 101 and break the CHECK
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> ledger.transfer(a, b, 50L, "pay"));
        assertNotNull(failure);
        // the debit rolled back with the whole transaction
        assertEquals(50L, ledger.balance(a));
        assertEquals(51L, ledger.balance(b));
    }

    @Test
    @DisplayName("withdraw is guarded so it can never go negative")
    void withdrawGuarded() {
        WaymarkLedger ledger = freshLedger();
        UUID a = UUID.randomUUID();
        ledger.deposit(a, 30L, "give", "seed");
        assertFalse(ledger.withdraw(null, a, 50L, "take", "admin").ok());
        assertEquals(30L, ledger.balance(a));
        assertTrue(ledger.withdraw(null, a, 30L, "take", "admin").ok());
        assertEquals(0L, ledger.balance(a));
    }

    @Test
    @DisplayName("setBalance rejects negatives")
    void setBalanceRejectsNegative() {
        WaymarkLedger ledger = freshLedger();
        UUID a = UUID.randomUUID();
        assertFalse(ledger.setBalance(a, -5L, "admin"));
        assertTrue(ledger.setBalance(a, 42L, "admin"));
        assertEquals(42L, ledger.balance(a));
    }
}
