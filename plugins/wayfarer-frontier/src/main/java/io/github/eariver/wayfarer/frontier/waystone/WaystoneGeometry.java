package io.github.eariver.wayfarer.frontier.waystone;

/**
 * R-10/R-12: pure geometry of a waystone structure.
 *
 * <p>A record stores the ANCHOR: the ground block at the centre of the 5x5 floor. The
 * structure is built one block above the anchor — core at {@code y+1}, player head at
 * {@code y+2}, cap at {@code y+3}, lanterns at the floor corners' tops. Every lookup and
 * every removal decision is expressed against this geometry so the core/anchor offset can
 * never break discovery, GUI actions, protection or cleanup.
 */
public final class WaystoneGeometry {

    private WaystoneGeometry() {
    }

    /** Half-width of the floor: |dx|,|dz| <= 2 around the anchor. */
    public static final int FOOTPRINT = 2;
    /** The core sits one block above the anchor. */
    public static final int CORE_DY = 1;
    /** Player head two blocks above the anchor. */
    public static final int HEAD_DY = 2;
    /** Cap three blocks above the anchor. */
    public static final int CAP_DY = 3;

    /** True when (x,y,z) is one of the blocks the structure is built from. */
    public static boolean isStructureBlock(int anchorX, int anchorY, int anchorZ, int x, int y, int z) {
        int dx = x - anchorX;
        int dz = z - anchorZ;
        int dy = y - anchorY;
        if (Math.abs(dx) > FOOTPRINT || Math.abs(dz) > FOOTPRINT) return false;
        if (dy == 0) return true;                          // the 5x5 floor
        if (dy == CORE_DY) {
            boolean core = dx == 0 && dz == 0;
            boolean lantern = (Math.abs(dx) == FOOTPRINT && dz == 0) || (dx == 0 && Math.abs(dz) == FOOTPRINT);
            return core || lantern;
        }
        if (dy == HEAD_DY) return dx == 0 && dz == 0;       // the player head
        if (dy == CAP_DY) return dx == 0 && dz == 0;        // the cap
        return false;
    }

    /**
     * R-12: a structure block is only cleared when it still holds the material that the
     * structure placed there. A player's build inside the footprint, or terrain that
     * replaced a floor block, survives removal.
     */
    public static boolean shouldClear(int anchorX, int anchorY, int anchorZ, int x, int y, int z,
                                      String material,
                                      String floorMaterial, String capMaterial, String coreMaterial) {
        if ("AIR".equals(material)) return false;
        if (!isStructureBlock(anchorX, anchorY, anchorZ, x, y, z)) return false;
        int dy = y - anchorY;
        int dx = x - anchorX;
        int dz = z - anchorZ;
        if (dy == 0) return material.equals(floorMaterial);
        if (dy == CORE_DY) {
            if (dx == 0 && dz == 0) return material.equals(coreMaterial);
            return material.equals("LANTERN");
        }
        if (dy == HEAD_DY && dx == 0 && dz == 0) return material.equals("PLAYER_HEAD");
        if (dy == CAP_DY && dx == 0 && dz == 0) return material.equals(capMaterial);
        return false;
    }
}
