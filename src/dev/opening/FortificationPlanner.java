package dev.opening;

import battlecode.common.MapLocation;
import battlecode.common.TrapType;

import java.util.ArrayList;
import java.util.List;

/** Deterministic geometry for layered flag fortifications. */
public final class FortificationPlanner {
    public enum Kind {
        STUN,
        EXPLOSIVE,
        WATER
    }

    public static final class Site {
        public final MapLocation location;
        public final Kind kind;
        public final int stage;
        public final int zone;

        private Site(MapLocation location, Kind kind, int stage, int zone) {
            this.location = location;
            this.kind = kind;
            this.stage = stage;
            this.zone = zone;
        }

        public int cost() {
            if (kind == Kind.WATER) return 20;
            return kind == Kind.STUN ? TrapType.STUN.buildCost : TrapType.EXPLOSIVE.buildCost;
        }
    }

    private FortificationPlanner() {
    }

    /** Bytecode-bounded runtime schedule; one O(1) site lookup per worker. */
    public static int runtimeSiteCount(MapLocation[] flags, int stageWithinCycle) {
        int perFlag = stageWithinCycle == 2 ? 8 : stageWithinCycle == 3 ? 12 : 4;
        return flags.length * perFlag;
    }

    public static Site runtimeSiteAt(
            MapLocation[] flags,
            int[] zones,
            int cycle,
            int stageWithinCycle,
            int index
    ) {
        int perFlag = stageWithinCycle == 2 ? 8 : stageWithinCycle == 3 ? 12 : 4;
        int flagIndex = index / perFlag;
        int local = index % perFlag;
        MapLocation flag = flags[flagIndex];
        int dx;
        int dy;
        Kind kind;
        if (stageWithinCycle == 0) {
            int radius = 1 + cycle * 5;
            dx = local == 0 ? radius : local == 1 ? -radius : 0;
            dy = local == 2 ? radius : local == 3 ? -radius : 0;
            kind = Kind.STUN;
        } else if (stageWithinCycle == 1) {
            int radius = 1 + cycle * 5;
            dx = local < 2 ? radius : -radius;
            dy = (local & 1) == 0 ? radius : -radius;
            kind = Kind.STUN;
        } else if (stageWithinCycle == 2) {
            int radius = 2 + cycle * 5;
            int direction = local;
            dx = direction == 0 || direction == 1 || direction == 7 ? radius
                    : direction == 3 || direction == 4 || direction == 5 ? -radius : 0;
            dy = direction == 1 || direction == 2 || direction == 3 ? radius
                    : direction == 5 || direction == 6 || direction == 7 ? -radius : 0;
            kind = Kind.EXPLOSIVE;
        } else {
            int radius = 3 + cycle * 5 + local / 4;
            int corner = local & 3;
            dx = corner < 2 ? radius : -radius;
            dy = (corner & 1) == 0 ? radius : -radius;
            kind = Kind.WATER;
        }
        return new Site(new MapLocation(flag.x + dx, flag.y + dy), kind,
                cycle * 4 + stageWithinCycle, zones[flagIndex]);
    }

    public static List<Site> generate(
            int width,
            int height,
            MapLocation[] flags,
            int[] zones,
            MapLocation[] spawnCenters,
            int maxCycles
    ) {
        List<Site> result = new ArrayList<>();
        int zoneCount = 1;
        for (int zone : zones) zoneCount = Math.max(zoneCount, zone + 1);
        for (int cycle = 0; cycle < maxCycles; cycle++) {
            for (int stageWithinCycle = 0; stageWithinCycle < 4; stageWithinCycle++) {
                for (int zone = 0; zone < zoneCount; zone++) {
                    appendZoneStage(result, width, height, flags, zones, spawnCenters,
                            zone, cycle, stageWithinCycle);
                }
            }
        }
        return result;
    }

    public static List<Site> generateCycle(
            int width,
            int height,
            MapLocation[] flags,
            int[] zones,
            MapLocation[] spawnCenters,
            int cycle
    ) {
        List<Site> result = new ArrayList<>();
        int zoneCount = 1;
        for (int zone : zones) zoneCount = Math.max(zoneCount, zone + 1);
        for (int stageWithinCycle = 0; stageWithinCycle < 4; stageWithinCycle++) {
            for (int zone = 0; zone < zoneCount; zone++) {
                appendZoneStage(result, width, height, flags, zones, spawnCenters,
                        zone, cycle, stageWithinCycle);
            }
        }
        return result;
    }

    public static List<Site> generateStage(
            int width,
            int height,
            MapLocation[] flags,
            int[] zones,
            MapLocation[] spawnCenters,
            int cycle,
            int stageWithinCycle
    ) {
        List<Site> result = new ArrayList<>();
        int zoneCount = 1;
        for (int zone : zones) zoneCount = Math.max(zoneCount, zone + 1);
        for (int zone = 0; zone < zoneCount; zone++) {
            appendZoneStage(result, width, height, flags, zones, spawnCenters,
                    zone, cycle, stageWithinCycle);
        }
        return result;
    }

    private static void appendZoneStage(
            List<Site> output,
            int width,
            int height,
            MapLocation[] flags,
            int[] zones,
            MapLocation[] spawnCenters,
            int zone,
            int cycle,
            int stageWithinCycle
    ) {
        int stage = cycle * 4 + stageWithinCycle;
        int stunRadius = 1 + cycle * 5;
        int explosiveRadius = 2 + cycle * 5;
        int waterMin = 3 + cycle * 5;
        int waterMax = 5 + cycle * 5;
        List<Site> unique = new ArrayList<>();

        if (stageWithinCycle == 0 || stageWithinCycle == 1 || stageWithinCycle == 2) {
            int radius = stageWithinCycle == 2 ? explosiveRadius : stunRadius;
            int[][] offsets;
            if (stageWithinCycle == 0) {
                offsets = new int[][]{{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}};
            } else if (stageWithinCycle == 1) {
                offsets = new int[][]{{radius, radius}, {radius, -radius},
                        {-radius, radius}, {-radius, -radius}};
            } else {
                offsets = new int[][]{{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius},
                        {radius, radius}, {radius, -radius}, {-radius, radius}, {-radius, -radius}};
            }
            for (int i = 0; i < flags.length; i++) {
                if (zones[i] != zone) continue;
                for (int[] delta : offsets) {
                    add(unique, width, height,
                            new MapLocation(flags[i].x + delta[0], flags[i].y + delta[1]),
                            stageWithinCycle == 2 ? Kind.EXPLOSIVE : Kind.STUN, stage, zone);
                }
            }
        } else {
            Bounds bounds = bounds(flags, zones, zone);
            if (bounds == null) return;
            MapLocation core = new MapLocation((bounds.minX + bounds.maxX) / 2,
                    (bounds.minY + bounds.maxY) / 2);
            MapLocation spawn = nearest(core, spawnCenters);
            for (int i = 0; i < flags.length; i++) {
                if (zones[i] != zone) continue;
                for (int distance = waterMin; distance <= waterMax; distance++) {
                    for (int delta = -distance; delta <= distance; delta++) {
                        MapLocation[] boundary = {
                                new MapLocation(flags[i].x - distance, flags[i].y + delta),
                                new MapLocation(flags[i].x + distance, flags[i].y + delta),
                                new MapLocation(flags[i].x + delta, flags[i].y - distance),
                                new MapLocation(flags[i].x + delta, flags[i].y + distance)
                        };
                        for (MapLocation location : boundary) {
                            if (isDiagonalGrid(location, zone) && !isCorridor(location, core, spawn)) {
                                add(unique, width, height, location, Kind.WATER, stage, zone);
                            }
                        }
                    }
                }
            }
        }
        output.addAll(unique);
    }

    private static boolean isDiagonalGrid(MapLocation location, int zone) {
        return Math.floorMod(location.x + location.y + zone, 3) == 0
                || Math.floorMod(location.x - location.y - zone, 3) == 0;
    }

    private static boolean isCorridor(MapLocation location, MapLocation core, MapLocation spawn) {
        int dx = Integer.compare(spawn.x, core.x);
        int dy = Integer.compare(spawn.y, core.y);
        boolean horizontal = dy == 0 || dx != 0;
        boolean vertical = dx == 0 || dy != 0;
        if (horizontal && location.y == core.y
                && Integer.compare(location.x, core.x) == dx) return true;
        return vertical && location.x == core.x
                && Integer.compare(location.y, core.y) == dy;
    }

    private static int distanceToZone(MapLocation location, MapLocation[] flags, int[] zones, int zone) {
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < flags.length; i++) {
            if (zones[i] == zone) best = Math.min(best, OpeningLayoutPlanner.chebyshev(location, flags[i]));
        }
        return best;
    }

    private static Bounds bounds(MapLocation[] flags, int[] zones, int zone) {
        Bounds result = null;
        for (int i = 0; i < flags.length; i++) {
            if (zones[i] != zone) continue;
            if (result == null) result = new Bounds(flags[i].x, flags[i].x, flags[i].y, flags[i].y);
            else {
                result.minX = Math.min(result.minX, flags[i].x);
                result.maxX = Math.max(result.maxX, flags[i].x);
                result.minY = Math.min(result.minY, flags[i].y);
                result.maxY = Math.max(result.maxY, flags[i].y);
            }
        }
        return result;
    }

    private static MapLocation nearest(MapLocation from, MapLocation[] locations) {
        MapLocation best = locations[0];
        int bestDistance = from.distanceSquaredTo(best);
        for (int i = 1; i < locations.length; i++) {
            int distance = from.distanceSquaredTo(locations[i]);
            if (distance < bestDistance) {
                best = locations[i];
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void add(
            List<Site> unique,
            int width,
            int height,
            MapLocation location,
            Kind kind,
            int stage,
            int zone
    ) {
        if (location.x < 0 || location.y < 0 || location.x >= width || location.y >= height) return;
        for (Site site : unique) {
            if (site.location.equals(location)) return;
        }
        unique.add(new Site(location, kind, stage, zone));
    }

    private static final class Bounds {
        int minX;
        int maxX;
        int minY;
        int maxY;

        Bounds(int minX, int maxX, int minY, int maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }
}
