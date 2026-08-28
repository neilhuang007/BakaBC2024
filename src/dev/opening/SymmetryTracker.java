package dev.opening;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;

/** Locally compares immutable terrain and shares only idempotent elimination bits. */
public final class SymmetryTracker {
    public static final int ROTATIONAL_BIT = 1;
    public static final int HORIZONTAL_BIT = 2;
    public static final int VERTICAL_BIT = 4;

    private final int width;
    private final int height;
    private static final int TABLE_SIZE = 256;
    private static final int TABLE_MASK = TABLE_SIZE - 1;
    private final short[] terrainKeys = new short[TABLE_SIZE];
    private final byte[] terrainValues = new byte[TABLE_SIZE];
    private int terrainSize;
    private int observationCursor;

    public SymmetryTracker(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void observe(MapInfo[] infos, OpeningSharedState shared) throws GameActionException {
        int eliminated = 0;
        int count = Math.min(8, infos.length);
        for (int n = 0; n < count; n++) {
            MapInfo info = infos[(observationCursor + n) % infos.length];
            MapLocation location = info.getMapLocation();
            byte signature = signature(info);
            put(index(location) + 1, signature);
            eliminated |= mismatch(location, signature, OpeningLayoutPlanner.Symmetry.ROTATIONAL,
                    ROTATIONAL_BIT);
            eliminated |= mismatch(location, signature, OpeningLayoutPlanner.Symmetry.HORIZONTAL,
                    HORIZONTAL_BIT);
            eliminated |= mismatch(location, signature, OpeningLayoutPlanner.Symmetry.VERTICAL,
                    VERTICAL_BIT);
        }
        if (infos.length > 0) observationCursor = (observationCursor + count) % infos.length;
        if (eliminated != 0) shared.eliminateSymmetries(eliminated);
    }

    public static OpeningLayoutPlanner.Symmetry confirmedSymmetry(int eliminatedMask) {
        int remaining = (~eliminatedMask) & 7;
        switch (remaining) {
            case ROTATIONAL_BIT:
                return OpeningLayoutPlanner.Symmetry.ROTATIONAL;
            case HORIZONTAL_BIT:
                return OpeningLayoutPlanner.Symmetry.HORIZONTAL;
            case VERTICAL_BIT:
                return OpeningLayoutPlanner.Symmetry.VERTICAL;
            default:
                return OpeningLayoutPlanner.Symmetry.UNKNOWN;
        }
    }

    private int mismatch(
            MapLocation location,
            byte signature,
            OpeningLayoutPlanner.Symmetry symmetry,
            int bit
    ) {
        int reflectedX = location.x;
        int reflectedY = location.y;
        if (symmetry == OpeningLayoutPlanner.Symmetry.VERTICAL
                || symmetry == OpeningLayoutPlanner.Symmetry.ROTATIONAL) {
            reflectedX = width - 1 - reflectedX;
        }
        if (symmetry == OpeningLayoutPlanner.Symmetry.HORIZONTAL
                || symmetry == OpeningLayoutPlanner.Symmetry.ROTATIONAL) {
            reflectedY = height - 1 - reflectedY;
        }
        byte other = get(reflectedX + reflectedY * width + 1);
        return other != 0 && other != signature ? bit : 0;
    }

    private int index(MapLocation location) {
        return location.x + location.y * width;
    }

    private void put(int key, byte value) {
        if (terrainSize >= TABLE_SIZE / 2) return;
        int slot = (key * 31) & TABLE_MASK;
        for (int i = 0; i < TABLE_SIZE; i++) {
            if (terrainKeys[slot] == 0 || terrainKeys[slot] == key) {
                if (terrainKeys[slot] == 0) terrainSize++;
                terrainKeys[slot] = (short) key;
                terrainValues[slot] = value;
                return;
            }
            slot = (slot + 1) & TABLE_MASK;
        }
    }

    private byte get(int key) {
        int slot = (key * 31) & TABLE_MASK;
        for (int i = 0; i < TABLE_SIZE; i++) {
            int stored = terrainKeys[slot] & 0xFFFF;
            if (stored == 0) return 0;
            if (stored == key) return terrainValues[slot];
            slot = (slot + 1) & TABLE_MASK;
        }
        return 0;
    }

    private static byte signature(MapInfo info) {
        if (info.isWall()) return 1;
        if (info.isDam()) return 2;
        if (info.isSpawnZone()) return 3;
        return 4;
    }
}
