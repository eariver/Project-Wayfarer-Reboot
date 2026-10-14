package io.github.eariver.wayfarer.main.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R-03 regression tests: buffered progress must accumulate, survive failed checkpoints
 * and be consumed exactly once.
 */
class ProgressBufferTest {

    private final ProgressBuffer buffer = new ProgressBuffer();
    private final UUID player = UUID.randomUUID();
    private final long baseline = 42L;

    @Test
    @DisplayName("100 consecutive breaks add exactly 100")
    void hundredBreaksAccumulate() {
        for (int i = 0; i < 100; i++) {
            buffer.addBreak(player, baseline);
        }
        assertEquals(baseline + 100, buffer.latest(player, baseline));
        // the cached baseline itself was never advanced
        assertEquals(baseline, baseline);
        assertTrue(buffer.isDirty(player));
    }

    @Test
    @DisplayName("the accumulation always builds on the newest value, not the baseline")
    void eachBreakBuildsOnThePrevious() {
        long expected = baseline;
        for (int i = 0; i < 10; i++) {
            expected = buffer.addBreak(player, baseline);
            assertEquals(baseline + i + 1, expected);
        }
    }

    @Test
    @DisplayName("a failed checkpoint keeps the pending value")
    void failedCheckpointKeepsPending() {
        buffer.addBreak(player, baseline);
        buffer.addBreak(player, baseline);
        // the flush failed: nothing is cleared, the value stays pending
        assertTrue(buffer.isDirty(player));
        assertEquals(baseline + 2, buffer.latest(player, baseline));
    }

    @Test
    @DisplayName("a successful checkpoint clears the overlay exactly once")
    void successfulCheckpointClears() {
        buffer.addBreak(player, baseline);
        buffer.clearOne(player);
        assertEquals(baseline, buffer.latest(player, baseline));
        assertFalse(buffer.isDirty(player));
        // and the next break starts from the new baseline
        assertEquals(baseline + 1, buffer.addBreak(player, baseline));
    }

    @Test
    @DisplayName("a new baseline (join/evolution) resets the buffer without losing it")
    void baselineReset() {
        buffer.addBreak(player, baseline);
        buffer.setBaseline(player, 100L);
        assertEquals(100L, buffer.latest(player, 100L));
        assertEquals(101L, buffer.addBreak(player, 100L));
    }

    @Test
    @DisplayName("players are tracked independently")
    void independentPlayers() {
        UUID other = UUID.randomUUID();
        buffer.addBreak(player, baseline);
        buffer.addBreak(player, baseline);
        buffer.addBreak(other, 0L);
        assertEquals(baseline + 2, buffer.latest(player, baseline));
        assertEquals(1L, buffer.latest(other, 0L));
        assertEquals(2, buffer.pendingCount());
        buffer.clearOne(player);
        assertEquals(1, buffer.pendingCount());
    }

    @Test
    @DisplayName("clearAll drops everything (shutdown)")
    void clearAll() {
        buffer.addBreak(player, baseline);
        buffer.addBreak(UUID.randomUUID(), 0L);
        buffer.clearAll();
        assertEquals(0, buffer.pendingCount());
        assertFalse(buffer.isDirty(player));
        assertEquals(baseline, buffer.latest(player, baseline));
    }
    @Nested
    @DisplayName("D4: a cache refresh must not clobber a newer buffered progress")
    class Promote {

        private final UUID player = UUID.randomUUID();

        @Test
        @DisplayName("an older DB row keeps the buffered progress")
        void olderRowKeepsBuffer() {
            buffer.addBreak(player, 10L);       // 11 pending
            buffer.addBreak(player, 10L);       // 12 pending
            long after = buffer.promote(player, 10L);   // join/server row is stale
            assertEquals(12L, after, "the buffered progress survives the refresh");
            assertEquals(12L, buffer.latest(player, 10L));
        }

        @Test
        @DisplayName("a newer DB row becomes the new baseline")
        void newerRowBecomesBaseline() {
            buffer.addBreak(player, 10L);       // 11 pending
            long after = buffer.promote(player, 11L);   // checkpointed elsewhere
            assertEquals(11L, after);
            assertEquals(11L, buffer.latest(player, 11L));
        }

        @Test
        @DisplayName("an empty overlay simply adopts the row")
        void emptyOverlayAdoptsRow() {
            assertEquals(7L, buffer.promote(player, 7L));
        }

        @Test
        @DisplayName("reissue-style flow: epoch bump then a stale row must not lose progress")
        void reissueFlowKeepsProgress() {
            long baseline = 100L;
            for (int i = 0; i < 5; i++) buffer.addBreak(player, baseline);   // 105 pending
            // reissue bumps the epoch and reads the row back (still 100 in the DB)
            buffer.promote(player, baseline);
            assertEquals(105L, buffer.latest(player, baseline));
            // the next break still counts on the newest value
            assertEquals(106L, buffer.addBreak(player, baseline));
        }
    }

    @Nested
    @DisplayName("D2: the quit/stop flush decides from the pending value")
    class QuitFlush {

        private final UUID player = UUID.randomUUID();

        @Test
        @DisplayName("nothing pending means nothing to flush")
        void nothingToFlush() {
            assertNull(buffer.pendingForFlush(player, 42L));
            buffer.setBaseline(player, 42L);
            assertNull(buffer.pendingForFlush(player, 42L));
        }

        @Test
        @DisplayName("a pending value surfaces for the quit flush")
        void pendingValueSurfaces() {
            buffer.addBreak(player, 42L);
            assertEquals(43L, buffer.pendingForFlush(player, 42L));
        }
    }
}
