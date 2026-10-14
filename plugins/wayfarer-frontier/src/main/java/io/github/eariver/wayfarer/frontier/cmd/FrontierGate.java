package io.github.eariver.wayfarer.frontier.cmd;

/**
 * R-05: permission decisions for the Frontier gate commands, kept free of Bukkit types
 * so they can be unit-tested without a server.
 */
public final class FrontierGate {

    private FrontierGate() {
    }

    /** Only a privileged sender may move ANOTHER player to the gate. */
    public static boolean mayTeleportOther(boolean hasAdminPermission) {
        return hasAdminPermission;
    }

    /** Self-travel is always allowed (the navigation GUI uses it). */
    public static boolean mayTravelSelf() {
        return true;
    }
}
