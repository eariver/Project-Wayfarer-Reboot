package io.github.eariver.wayfarer.waymark;

/** Result of a ledger operation; the balance is only meaningful when {@code ok}. */
public record WaymarkResult(boolean ok, String error, long balance, String reference) {

    public static WaymarkResult ok(long balance) {
        return new WaymarkResult(true, null, balance, null);
    }

    public static WaymarkResult ok(long balance, String reference) {
        return new WaymarkResult(true, null, balance, reference);
    }

    public static WaymarkResult error(String message) {
        return new WaymarkResult(false, message, 0L, null);
    }
}
