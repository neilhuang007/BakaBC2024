package dev.opening;

import battlecode.common.MapLocation;

/** Pure, deterministic planning for the three opening flag destinations. */
public final class OpeningLayoutPlanner {
    public enum Symmetry {
        UNKNOWN,
        ROTATIONAL,
        HORIZONTAL,
        VERTICAL
    }

    public static final class Layout {
        public final MapLocation[] primaryTargets;
        public final MapLocation[] conservativeTargets;
        public final boolean[] secondary;
        public final int zoneCount;

        private Layout(
                MapLocation[] primaryTargets,
                MapLocation[] conservativeTargets,
                boolean[] secondary,
                int zoneCount
        ) {
            this.primaryTargets = primaryTargets;
            this.conservativeTargets = conservativeTargets;
            this.secondary = secondary;
            this.zoneCount = zoneCount;
        }
    }

    private enum Corner {
        NORTH_WEST,
        NORTH_EAST,
        SOUTH_WEST,
        SOUTH_EAST
    }

    private final int width;
    private final int height;
    private final MapLocation[] spawnCenters;

    public OpeningLayoutPlanner(int width, int height, MapLocation[] spawnCenters) {
        if (spawnCenters == null || spawnCenters.length != 3) {
            throw new IllegalArgumentException("exactly three spawn centers are required");
        }
        this.width = width;
        this.height = height;
        this.spawnCenters = spawnCenters.clone();
    }

    public Layout plan(Symmetry symmetry) {
        Corner conservative = safestCorner(Symmetry.UNKNOWN, null);
        MapLocation[] conservativeTargets = targetsForCorner(conservative);
        if (symmetry == Symmetry.UNKNOWN || symmetry == Symmetry.ROTATIONAL) {
            Corner selected = symmetry == Symmetry.UNKNOWN
                    ? conservative
                    : safestCorner(symmetry, null);
            return new Layout(targetsForCorner(selected), conservativeTargets, new boolean[3], 1);
        }

        Corner primary = safestCorner(symmetry, null);
        Corner secondaryCorner = safestCorner(symmetry, primary);
        MapLocation[] primarySlots = targetsForCorner(primary);
        MapLocation secondaryTarget = cornerPoint(secondaryCorner, 1, 0);

        int bestSecondaryFlag = 0;
        int bestSwap = 0;
        int bestCost = Integer.MAX_VALUE;
        for (int secondaryFlag = 0; secondaryFlag < 3; secondaryFlag++) {
            int[] remaining = new int[2];
            int ri = 0;
            for (int i = 0; i < 3; i++) {
                if (i != secondaryFlag) remaining[ri++] = i;
            }
            for (int swap = 0; swap < 2; swap++) {
                int cost = chebyshev(spawnCenters[secondaryFlag], secondaryTarget);
                cost += chebyshev(spawnCenters[remaining[0]], primarySlots[swap]);
                cost += chebyshev(spawnCenters[remaining[1]], primarySlots[1 - swap]);
                if (cost < bestCost
                        || (cost == bestCost && secondaryFlag < bestSecondaryFlag)
                        || (cost == bestCost && secondaryFlag == bestSecondaryFlag && swap < bestSwap)) {
                    bestCost = cost;
                    bestSecondaryFlag = secondaryFlag;
                    bestSwap = swap;
                }
            }
        }

        MapLocation[] result = new MapLocation[3];
        boolean[] secondary = new boolean[3];
        int[] remaining = new int[2];
        int ri = 0;
        for (int i = 0; i < 3; i++) {
            if (i != bestSecondaryFlag) remaining[ri++] = i;
        }
        result[bestSecondaryFlag] = secondaryTarget;
        secondary[bestSecondaryFlag] = true;
        result[remaining[0]] = primarySlots[bestSwap];
        result[remaining[1]] = primarySlots[1 - bestSwap];
        return new Layout(result, conservativeTargets, secondary, 2);
    }

    public int nearestSpawnCenter(MapLocation location) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < spawnCenters.length; i++) {
            int distance = location.distanceSquaredTo(spawnCenters[i]);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Strong spawn-envelope prior; ambiguous envelopes remain rotational/conservative. */
    public Symmetry inferSymmetryFromSpawns() {
        int sumX = 0;
        int sumY = 0;
        for (MapLocation center : spawnCenters) {
            sumX += center.x;
            sumY += center.y;
        }
        int doubledXEdge = Math.min(sumX, 3 * (width - 1) - sumX);
        int doubledYEdge = Math.min(sumY, 3 * (height - 1) - sumY);
        if (doubledYEdge * 4 < doubledXEdge * 3) return Symmetry.HORIZONTAL;
        if (doubledXEdge * 4 < doubledYEdge * 3) return Symmetry.VERTICAL;
        return Symmetry.ROTATIONAL;
    }

    public static MapLocation[] computeSpawnCenters(MapLocation[] spawnLocations) {
        if (spawnLocations == null || spawnLocations.length != 27) {
            throw new IllegalArgumentException("expected 27 allied spawn locations");
        }
        boolean[] used = new boolean[spawnLocations.length];
        MapLocation[] centers = new MapLocation[3];
        for (int group = 0; group < 3; group++) {
            int seed = -1;
            for (int i = 0; i < spawnLocations.length; i++) {
                if (!used[i]) {
                    seed = i;
                    break;
                }
            }
            if (seed < 0) throw new IllegalArgumentException("not enough spawn groups");
            int sx = 0;
            int sy = 0;
            int count = 0;
            for (int i = 0; i < spawnLocations.length; i++) {
                if (!used[i] && spawnLocations[seed].distanceSquaredTo(spawnLocations[i]) <= 8) {
                    used[i] = true;
                    sx += spawnLocations[i].x;
                    sy += spawnLocations[i].y;
                    count++;
                }
            }
            if (count != 9) throw new IllegalArgumentException("spawn group did not contain nine tiles");
            centers[group] = new MapLocation(sx / count, sy / count);
        }
        for (int i = 0; i < centers.length; i++) {
            for (int j = i + 1; j < centers.length; j++) {
                if (centers[j].x < centers[i].x
                        || (centers[j].x == centers[i].x && centers[j].y < centers[i].y)) {
                    MapLocation swap = centers[i];
                    centers[i] = centers[j];
                    centers[j] = swap;
                }
            }
        }
        return centers;
    }

    public static MapLocation reflect(MapLocation location, Symmetry symmetry, int width, int height) {
        switch (symmetry) {
            case HORIZONTAL:
                return new MapLocation(location.x, height - 1 - location.y);
            case VERTICAL:
                return new MapLocation(width - 1 - location.x, location.y);
            case ROTATIONAL:
                return new MapLocation(width - 1 - location.x, height - 1 - location.y);
            default:
                return location;
        }
    }

    public static int chebyshev(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    private Corner safestCorner(Symmetry symmetry, Corner excluded) {
        Corner best = null;
        int bestSafety = Integer.MIN_VALUE;
        int bestTravel = Integer.MAX_VALUE;
        for (Corner corner : Corner.values()) {
            if (corner == excluded) continue;
            MapLocation anchor = cornerPoint(corner, 1, 1);
            int safety = symmetry == Symmetry.UNKNOWN
                    ? worstCaseSafety(anchor)
                    : safety(anchor, symmetry);
            int travel = 0;
            for (MapLocation center : spawnCenters) travel += chebyshev(center, anchor);
            if (safety > bestSafety
                    || (safety == bestSafety && travel < bestTravel)
                    || (safety == bestSafety && travel == bestTravel
                    && (best == null || corner.ordinal() < best.ordinal()))) {
                best = corner;
                bestSafety = safety;
                bestTravel = travel;
            }
        }
        return best;
    }

    private int worstCaseSafety(MapLocation anchor) {
        int result = Integer.MAX_VALUE;
        result = Math.min(result, safety(anchor, Symmetry.ROTATIONAL));
        result = Math.min(result, safety(anchor, Symmetry.HORIZONTAL));
        result = Math.min(result, safety(anchor, Symmetry.VERTICAL));
        return result;
    }

    private int safety(MapLocation anchor, Symmetry symmetry) {
        int nearest = Integer.MAX_VALUE;
        for (MapLocation center : spawnCenters) {
            MapLocation enemy = reflect(center, symmetry, width, height);
            nearest = Math.min(nearest, anchor.distanceSquaredTo(enemy));
        }
        return nearest;
    }

    private MapLocation[] targetsForCorner(Corner corner) {
        return new MapLocation[]{
                cornerPoint(corner, 1, 0),
                cornerPoint(corner, 13, 0),
                cornerPoint(corner, 7, 0)
        };
    }

    private MapLocation cornerPoint(Corner corner, int inwardX, int inwardY) {
        int x = corner == Corner.NORTH_WEST || corner == Corner.SOUTH_WEST
                ? inwardX
                : width - 1 - inwardX;
        int y = corner == Corner.NORTH_WEST || corner == Corner.NORTH_EAST
                ? inwardY
                : height - 1 - inwardY;
        return new MapLocation(x, y);
    }
}
