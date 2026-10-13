package io.github.eariver.wayfarer.main.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * R-03: the in-memory progress overlay for the Growth Pickaxe.
 *
 * <p>Mining writes are buffered until a checkpoint, so {@link #addBreak} must accumulate
 * on top of the NEWEST value (the buffered one when present), never on top of the last
 * value that was persisted — otherwise breaks between checkpoints overwrite each other.
 * A failed checkpoint keeps the pending value so the progress is never lost.
 */
public final class ProgressBuffer {

    private final Map<UUID, Long> pending = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();

    /** Newest progress for a player: buffered value when a checkpoint is pending. */
    public synchronized long latest(UUID owner, long baseline) {
        Long buffered = pending.get(owner);
        return buffered != null ? buffered : baseline;
    }

    /**
     * Accumulate one break on top of the newest value and mark the player dirty.
     *
     * @param baseline the last persisted/cached progress (used only when nothing is buffered)
     * @return the new progress value
     */
    public synchronized long addBreak(UUID owner, long baseline) {
        long next = latest(owner, baseline) + 1;
        pending.put(owner, next);
        dirty.add(owner);
        return next;
    }

    /** Seed the overlay from an authoritative tool (cache/join/checkpoint success). */
    public synchronized void setBaseline(UUID owner, long progress) {
        pending.put(owner, progress);
    }

    public synchronized boolean isDirty(UUID owner) {
        return dirty.contains(owner);
    }

    public synchronized Set<UUID> dirtyOwners() {
        return new HashSet<>(dirty);
    }

    /** Buffered value, or null when nothing is pending. */
    public synchronized Long pendingOf(UUID owner) {
        return pending.get(owner);
    }

    /** Snapshot of every pending value (used by the final flush on shutdown). */
    public synchronized java.util.Map<UUID, Long> pendingOfAll() {
        return new HashMap<>(pending);
    }

    /** Drop one player's overlay (called after a successful checkpoint). */
    public synchronized void clearOne(UUID owner) {
        pending.remove(owner);
        dirty.remove(owner);
    }

    /** Drop everything (called for a quitting player after the final flush). */
    public synchronized void clearAll() {
        pending.clear();
        dirty.clear();
    }

    public synchronized int pendingCount() {
        return pending.size();
    }
}
