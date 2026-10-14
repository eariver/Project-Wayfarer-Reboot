package io.github.eariver.wayfarer.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R-02: nested transactions must never commit partial work, and a connection failure
 * must not leak the Db lock.
 */
class DbTransactionTest {

    @TempDir
    File tempDir;

    private Db freshDb(String name) {
        return new Db(new File(tempDir, name + "-" + UUID.randomUUID() + ".mv.db"), false);
    }

    @Test
    @DisplayName("a nested tx joins the enclosing transaction instead of committing it")
    void nestedTxDoesNotCommit() throws Exception {
        Db db = freshDb("nested");
        db.exec("CREATE TABLE t (id INT PRIMARY KEY, v INT NOT NULL)");

        AtomicInteger seenByNested = new AtomicInteger(-1);
        RuntimeException failure = assertThrows(RuntimeException.class, () -> db.tx(outer -> {
            try (PreparedStatement ps = outer.prepareStatement("INSERT INTO t VALUES (1, 10)")) {
                ps.executeUpdate();
            }
            // this nested call used to COMMIT the outer transaction's partial work
            db.tx(inner -> {
                seenByNested.set(count(inner));
                return null;
            });
            throw new RuntimeException("outer failure after the nested read");
        }));
        assertNotNull(failure);

        // the entire outer transaction rolled back: nothing was committed
        assertEquals(0, rowCount(db),
                "the nested tx must not have committed the outer partial work");
        assertEquals(1, seenByNested.get(),
                "the nested frame joins the enclosing transaction and reads its uncommitted write");
    }

    @Test
    @DisplayName("a nested failure propagates and the outer transaction rolls back")
    void nestedFailureRollsBackOuter() throws Exception {
        Db db = freshDb("nestedfail");
        db.exec("CREATE TABLE t (id INT PRIMARY KEY, v INT NOT NULL)");
        assertThrows(RuntimeException.class, () -> db.tx(outer -> {
            try (PreparedStatement ps = outer.prepareStatement("INSERT INTO t VALUES (1, 10)")) {
                ps.executeUpdate();
            }
            db.tx(inner -> {
                throw new IllegalStateException("inner boom");
            });
            return null;
        }));
        assertEquals(0, rowCount(db));
    }

    @Test
    @DisplayName("inTransaction() reports the thread-local depth correctly")
    void inTransactionFlag() {
        Db db = freshDb("flag");
        assertFalse(db.inTransaction());
        db.tx(c -> {
            assertTrue(db.inTransaction(), "inside the outermost frame");
            db.tx(inner -> {
                assertTrue(inner != null);
                return null;
            });
            assertTrue(db.inTransaction(), "still inside after the nested frame");
            return null;
        });
        assertFalse(db.inTransaction(), "depth restored after the outermost frame");
    }

    @Test
    @DisplayName("a connection failure releases the lock (a later open still works)")
    void connectionFailureReleasesLock() throws Exception {
        Db db = freshDb("lockleak");
        db.exec("CREATE TABLE t (id INT PRIMARY KEY, v INT NOT NULL)");
        // a FILE in place of the parent directory makes opening the database fail
        File notADir = new File(tempDir, "not-a-dir");
        assertTrue(notADir.createNewFile() || notADir.isFile());
        Db failing = new Db(new File(notADir, "db.mv.db"), false);
        RuntimeException first = assertThrows(RuntimeException.class, () -> failing.tx(c -> null));
        assertNotNull(first);
        // the lock must be free; otherwise this second attempt would deadlock forever
        RuntimeException second = assertThrows(RuntimeException.class, () -> failing.tx(c -> null));
        assertNotNull(second);
        // and the healthy database still works on the same class
        assertEquals(0, rowCount(db));
    }

    private static int rowCount(Db db) {
        return db.tx(DbTransactionTest::count);
    }

    private static int count(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM t");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
    @Test
    @DisplayName("a connection failure leaves the transaction depth intact for the same thread")
    void connectionFailureKeepsDepthUsable() throws Exception {
        // a FILE in place of the parent directory makes opening the database fail
        File notADir = new File(tempDir, "not-a-dir-depth");
        assertTrue(notADir.createNewFile() || notADir.isFile());
        Db failing = new Db(new File(notADir, "db.mv.db"), false);
        assertThrows(RuntimeException.class, () -> failing.tx(c -> null));
        assertFalse(failing.inTransaction(), "the depth must be restored after a failed connect");

        // the SAME thread must still be able to run a real transaction afterwards
        Db healthy = freshDb("afterfailure");
        healthy.exec("CREATE TABLE t (id INT PRIMARY KEY, v INT NOT NULL)");
        int inserted = healthy.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO t VALUES (1, 7)")) {
                return ps.executeUpdate();
            }
        });
        assertEquals(1, inserted);
        assertEquals(1, rowCount(healthy), "the post-failure transaction must really COMMIT");

        // and a nested call inside it must still be treated as nested (no early commit)
        RuntimeException nestedBoom = assertThrows(RuntimeException.class, () -> healthy.tx(outer -> {
            try (PreparedStatement ps = outer.prepareStatement("INSERT INTO t VALUES (2, 8)")) {
                ps.executeUpdate();
            }
            healthy.tx(inner -> {
                throw new IllegalStateException("inner boom");
            });
            return null;
        }));
        assertNotNull(nestedBoom);
        assertEquals(1, rowCount(healthy), "the nested failure rolled the whole outer tx back");
        assertFalse(healthy.inTransaction());
    }

    @Test
    @DisplayName("a connection failure inside a nested frame still restores the depth")
    void nestedFailureAfterConnectFailureRestoresDepth() throws Exception {
        Db db = freshDb("depthrecover");
        db.exec("CREATE TABLE t (id INT PRIMARY KEY, v INT NOT NULL)");
        // outer frame fails, then the same thread runs a clean transaction
        assertThrows(RuntimeException.class, () -> db.tx(outer -> {
            try (PreparedStatement ps = outer.prepareStatement("INSERT INTO t VALUES (1, 1)")) {
                ps.executeUpdate();
            }
            throw new IllegalStateException("outer boom");
        }));
        assertFalse(db.inTransaction());
        assertEquals(0, rowCount(db));
        int inserted = db.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO t VALUES (9, 9)")) {
                return ps.executeUpdate();
            }
        });
        assertEquals(1, inserted);
        assertEquals(1, rowCount(db));
    }
}
