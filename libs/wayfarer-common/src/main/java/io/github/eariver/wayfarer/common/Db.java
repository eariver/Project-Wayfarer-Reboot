package io.github.eariver.wayfarer.common;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared H2 helper. Uses AUTO_SERVER mixed mode so that a second server process
 * (for example the Waymark ledger shared by Main and Frontier) can open the same
 * database file concurrently. Each Db instance is single-connection and guarded
 * by a lock; cross-process writes serialise at the H2 lock level.
 */
public final class Db {

    static {
        // Bukkit plugin classloaders are not visible to DriverManager's ServiceLoader,
        // so register the driver explicitly.
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("H2 driver not bundled", e);
        }
    }

    private final File file;
    private final boolean autoServer;
    private final ReentrantLock lock = new ReentrantLock(true);
    private Connection connection;

    public Db(File file, boolean autoServer) {
        this.file = file;
        this.autoServer = autoServer;
    }

    public Db(File file) {
        this(file, false);
    }

    public Connection connection() {
        lock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                String url = "jdbc:h2:" + file.getAbsolutePath().replaceAll("\\.mv\\.db$", "")
                        + (autoServer ? ";AUTO_SERVER=TRUE" : "");
                connection = DriverManager.getConnection(url, "sa", "");
            }
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open H2 database " + file, e);
        } finally {
            lock.unlock();
        }
    }

    /** Run a unit of work inside a transaction. */
    public <T> T tx(SqlFunction<T> body) {
        lock.lock();
        Connection c = connection();
        try {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T result = body.apply(c);
                c.commit();
                return result;
            } catch (Throwable t) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                }
                throw t instanceof RuntimeException r ? r : new RuntimeException(t);
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
    }

    public void exec(String... statements) {
        tx(c -> {
            try (Statement st = c.createStatement()) {
                for (String s : statements) st.execute(s);
            }
            return null;
        });
    }

    public void close() {
        lock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                try (Statement st = connection.createStatement()) {
                    st.execute("SHUTDOWN COMPACT");
                } catch (SQLException ignored) {
                }
                connection.close();
            }
        } catch (SQLException ignored) {
        } finally {
            lock.unlock();
        }
    }

    public File file() {
        return file;
    }

    public interface SqlFunction<T> {
        T apply(Connection c) throws Exception;
    }
}
