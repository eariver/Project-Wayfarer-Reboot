package io.github.eariver.wayfarer.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** R-14: a click only counts when it lands on the GUI's own top inventory. */
class GuiClicksTest {

    @Test
    @DisplayName("a click on the GUI's top inventory inside its slots counts")
    void topInventoryClickCounts() {
        assertTrue(GuiClicks.countsAsButtonClick(true, 4, 27));
        assertTrue(GuiClicks.countsAsButtonClick(true, 26, 27));
    }

    @Test
    @DisplayName("a click on the player's own inventory never counts")
    void bottomInventoryClickNeverCounts() {
        assertFalse(GuiClicks.countsAsButtonClick(false, 4, 27),
                "the player's own inventory slot must not trigger the GUI action");
        assertFalse(GuiClicks.countsAsButtonClick(false, 27, 27));
        assertFalse(GuiClicks.countsAsButtonClick(false, 35, 27));
    }

    @Test
    @DisplayName("a click outside the window or beyond the top inventory never counts")
    void outsideClickNeverCounts() {
        assertFalse(GuiClicks.countsAsButtonClick(true, -1, 27), "outside the window");
        assertFalse(GuiClicks.countsAsButtonClick(true, 27, 27), "first slot of the bottom inventory");
        assertFalse(GuiClicks.countsAsButtonClick(true, -999, 27));
        assertFalse(GuiClicks.countsAsButtonClick(true, 4, 0), "empty GUI");
    }
}
