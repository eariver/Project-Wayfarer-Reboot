package io.github.eariver.wayfarer.waymark;

import io.github.eariver.wayfarer.common.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * SQLite/H2 ledger shared by Main and Frontier (PoC storage; production uses the
 * Redis-backed RedisEconomy service — see docs/DECISIONS.md D-2026-10-11-03).
 *
 * <p>{@link #transfer} executes the debit and the credit inside ONE database
 * transaction, so a mid-transfer failure cannot make currency disappear (M3).
 */
public final class WaymarkLedger {

    private final Db store;
    private final String serverId;

    public WaymarkLedger(Db store, String serverId) {
        this.store = store;
        this.serverId = serverId;
        store.exec(
                "CREATE TABLE IF NOT EXISTS wm_balance (" +
                        "holder TEXT PRIMARY KEY, balance INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS wm_ledger (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, ts INTEGER NOT NULL, server_id TEXT NOT NULL, " +
                        "kind TEXT NOT NULL, actor TEXT, target TEXT NOT NULL, amount INTEGER NOT NULL, " +
                        "reference TEXT)"
        );
    }

    public Db store() {
        return store;
    }

    public long balance(UUID holder) {
        return store.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM wm_balance WHERE holder=?")) {
                ps.setString(1, holder.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    public WaymarkResult deposit(UUID target, long amount, String kind, String reference) {
        if (amount <= 0) return WaymarkResult.error("amount must be positive");
        if (target == null) return WaymarkResult.error("target is required");
        return store.tx(c -> {
            ensureRow(c, target);
            addBalance(c, target, amount);
            ledger(c, kind, null, target, amount, reference);
            return WaymarkResult.ok(balance(target));
        });
    }

    public WaymarkResult withdraw(UUID actor, UUID target, long amount, String kind, String reference) {
        if (amount <= 0) return WaymarkResult.error("amount must be positive");
        if (target == null) return WaymarkResult.error("target is required");
        return store.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE wm_balance SET balance=balance-?, updated_at=? WHERE holder=? AND balance>=?")) {
                ps.setLong(1, amount);
                ps.setLong(2, now());
                ps.setString(3, target.toString());
                ps.setLong(4, amount);
                if (ps.executeUpdate() == 0) {
                    return WaymarkResult.error("insufficient funds");
                }
            }
            ledger(c, kind, actor, target, -amount, reference);
            return WaymarkResult.ok(balance(target));
        });
    }

    /**
     * Player-to-player transfer as a single transaction: the guarded debit, the credit
     * and both ledger rows commit or roll back together.
     */
    public WaymarkResult transfer(UUID from, UUID to, long amount, String reference) {
        if (from == null || to == null) return WaymarkResult.error("invalid participants");
        if (from.equals(to)) return WaymarkResult.error("cannot pay yourself");
        if (amount <= 0) return WaymarkResult.error("amount must be positive");
        String ref = reference == null ? "pay" : reference;
        return store.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE wm_balance SET balance=balance-?, updated_at=? WHERE holder=? AND balance>=?")) {
                ps.setLong(1, amount);
                ps.setLong(2, now());
                ps.setString(3, from.toString());
                ps.setLong(4, amount);
                if (ps.executeUpdate() == 0) {
                    return WaymarkResult.error("insufficient funds");
                }
            }
            ensureRow(c, to);
            addBalance(c, to, amount);
            ledger(c, "pay", from, to, amount, from.toString());
            ledger(c, "pay", from, from, -amount, to.toString());
            return WaymarkResult.ok(balance(to), ref);
        });
    }

    public boolean setBalance(UUID target, long amount, String reference) {
        if (target == null || amount < 0) return false;
        return store.tx(c -> {
            ensureRow(c, target);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE wm_balance SET balance=?, updated_at=? WHERE holder=?")) {
                ps.setLong(1, amount);
                ps.setLong(2, now());
                ps.setString(3, target.toString());
                ps.executeUpdate();
            }
            ledger(c, "set", null, target, amount, reference == null ? "set" : reference);
            return true;
        });
    }

    /**
     * Creates the holder's row when absent. This must NOT touch an existing row's
     * balance — a MERGE upsert resets the columns it lists, which used to wipe an
     * existing balance on every deposit (currency loss). Hence the existence check.
     */
    private void ensureRow(Connection c, UUID target) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM wm_balance WHERE holder=?")) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO wm_balance(holder, balance, updated_at) VALUES(?, 0, ?)")) {
            ps.setString(1, target.toString());
            ps.setLong(2, now());
            ps.executeUpdate();
        }
    }

    private void addBalance(Connection c, UUID target, long delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE wm_balance SET balance=balance+?, updated_at=? WHERE holder=?")) {
            ps.setLong(1, delta);
            ps.setLong(2, now());
            ps.setString(3, target.toString());
            ps.executeUpdate();
        }
    }

    private void ledger(Connection c, String kind, UUID actor, UUID target, long amount, String ref)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO wm_ledger(ts, server_id, kind, actor, target, amount, reference) VALUES(?,?,?,?,?,?,?)")) {
            ps.setLong(1, now());
            ps.setString(2, serverId);
            ps.setString(3, kind);
            ps.setString(4, actor == null ? null : actor.toString());
            ps.setString(5, target.toString());
            ps.setLong(6, amount);
            ps.setString(7, ref);
            ps.executeUpdate();
        }
    }

    private long balance(Connection c, UUID holder) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM wm_balance WHERE holder=?")) {
            ps.setString(1, holder.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private long now() {
        return System.currentTimeMillis() / 1000L;
    }
}
