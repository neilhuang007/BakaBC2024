package dev.opening;

import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class OpeningLayoutPlannerTest {
    @Test
    public void computesStableSpawnCenters() {
        List<MapLocation> locations = new ArrayList<>();
        addSpawn(locations, 5, 5);
        addSpawn(locations, 5, 15);
        addSpawn(locations, 15, 5);
        MapLocation[] centers = OpeningLayoutPlanner.computeSpawnCenters(
                locations.toArray(new MapLocation[0]));
        assertArrayEquals(new MapLocation[]{
                new MapLocation(5, 5), new MapLocation(5, 15), new MapLocation(15, 5)
        }, centers);
    }

    @Test
    public void rotationalAndUnknownUseOneLegalCluster() {
        OpeningLayoutPlanner planner = planner();
        for (OpeningLayoutPlanner.Symmetry symmetry : new OpeningLayoutPlanner.Symmetry[]{
                OpeningLayoutPlanner.Symmetry.UNKNOWN,
                OpeningLayoutPlanner.Symmetry.ROTATIONAL}) {
            OpeningLayoutPlanner.Layout layout = planner.plan(symmetry);
            assertEquals(1, layout.zoneCount);
            assertLegalSpacing(layout.primaryTargets);
            assertFlagsTouchCornerWalls(layout.primaryTargets, 31, 31);
        }
    }

    @Test
    public void axisSymmetryUsesTwoPlusOne() {
        OpeningLayoutPlanner planner = planner();
        for (OpeningLayoutPlanner.Symmetry symmetry : new OpeningLayoutPlanner.Symmetry[]{
                OpeningLayoutPlanner.Symmetry.HORIZONTAL,
                OpeningLayoutPlanner.Symmetry.VERTICAL}) {
            OpeningLayoutPlanner.Layout layout = planner.plan(symmetry);
            assertEquals(2, layout.zoneCount);
            int secondary = 0;
            for (boolean value : layout.secondary) if (value) secondary++;
            assertEquals(1, secondary);
            assertLegalSpacing(layout.primaryTargets);
        }
    }

    @Test
    public void reflectionMatchesBattlecodeMapSymmetry() {
        MapLocation location = new MapLocation(3, 7);
        assertEquals(new MapLocation(3, 23), OpeningLayoutPlanner.reflect(location,
                OpeningLayoutPlanner.Symmetry.HORIZONTAL, 41, 31));
        assertEquals(new MapLocation(37, 7), OpeningLayoutPlanner.reflect(location,
                OpeningLayoutPlanner.Symmetry.VERTICAL, 41, 31));
        assertEquals(new MapLocation(37, 23), OpeningLayoutPlanner.reflect(location,
                OpeningLayoutPlanner.Symmetry.ROTATIONAL, 41, 31));
    }

    @Test
    public void spawnEnvelopeProvidesStrongAxisPrior() {
        assertEquals(OpeningLayoutPlanner.Symmetry.HORIZONTAL,
                new OpeningLayoutPlanner(31, 59, new MapLocation[]{
                        new MapLocation(15, 38), new MapLocation(4, 54), new MapLocation(26, 54)
                }).inferSymmetryFromSpawns());
        assertEquals(OpeningLayoutPlanner.Symmetry.VERTICAL,
                new OpeningLayoutPlanner(59, 31, new MapLocation[]{
                        new MapLocation(3, 3), new MapLocation(7, 15), new MapLocation(3, 27)
                }).inferSymmetryFromSpawns());
        assertEquals(OpeningLayoutPlanner.Symmetry.ROTATIONAL,
                new OpeningLayoutPlanner(31, 31, new MapLocation[]{
                        new MapLocation(3, 18), new MapLocation(3, 27), new MapLocation(12, 27)
                }).inferSymmetryFromSpawns());
    }

    private static OpeningLayoutPlanner planner() {
        return new OpeningLayoutPlanner(31, 31, new MapLocation[]{
                new MapLocation(5, 5), new MapLocation(5, 15), new MapLocation(15, 5)
        });
    }

    private static void assertLegalSpacing(MapLocation[] locations) {
        for (int i = 0; i < locations.length; i++) {
            for (int j = i + 1; j < locations.length; j++) {
                assertTrue(locations[i].distanceSquaredTo(locations[j])
                        >= GameConstants.MIN_FLAG_SPACING_SQUARED);
            }
        }
    }

    private static void assertFlagsTouchCornerWalls(MapLocation[] locations, int width, int height) {
        for (MapLocation location : locations) {
            assertTrue(location.x == 0 || location.y == 0
                    || location.x == width - 1 || location.y == height - 1);
        }
    }

    private static void addSpawn(List<MapLocation> output, int centerX, int centerY) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                output.add(new MapLocation(centerX + dx, centerY + dy));
            }
        }
    }
}
