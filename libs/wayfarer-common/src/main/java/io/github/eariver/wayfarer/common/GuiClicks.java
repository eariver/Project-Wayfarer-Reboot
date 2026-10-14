package io.github.eariver.wayfarer.common;

/**
 * R-14: the click-zone rule shared by every inventory GUI.
 *
 * <p>{@code InventoryClickEvent#getCurrentItem()} also reports the item under the cursor
 * when the player clicks their OWN inventory, so a matching material in the player's bag
 * must never trigger a GUI action. A click only counts when it happened on the GUI's top
 * inventory and inside a real slot.
 */
public final class GuiClicks {

    private GuiClicks() {
    }

    /**
     * @param clickedTop      whether the click happened on the GUI's own top inventory
     * @param rawSlot         the raw slot that was clicked ({@code -1} = outside the window)
     * @param topInventorySize number of slots in the GUI's top inventory
     * @return true when the click may trigger a GUI action
     */
    public static boolean countsAsButtonClick(boolean clickedTop, int rawSlot, int topInventorySize) {
        return clickedTop && rawSlot >= 0 && rawSlot < topInventorySize;
    }
}
