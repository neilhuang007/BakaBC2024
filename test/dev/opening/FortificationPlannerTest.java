package dev.opening;

import battlecode.common.MapLocation;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class FortificationPlannerTest {
    private static final MapLocation[] FLAGS = {
            new MapLocation(1, 0), new MapLocation(13, 0), new MapLocation(7, 0)
    };
    private static final MapLocation[] SPAWNS = {
            new MapLocation(10, 10), new MapLocation(10, 20), new MapLocation(20, 10)
    };

    @Test
    public void stagesAreOrderedAndSitesAreUniqueWithinStage() {
        List<FortificationPlanner.Site> sites = FortificationPlanner.generateCycle(
                31, 31, FLAGS, new int[]{0, 0, 0}, SPAWNS, 0);
        int lastStage = -1;
        Set<String> stageLocations = new HashSet<>();
        for (FortificationPlanner.Site site : sites) {
            if (site.stage != lastStage) {
                assertTrue(site.stage > lastStage);
                stageLocations.clear();
                lastStage = site.stage;
            }
            assertTrue(stageLocations.add(site.location.x + ":" + site.location.y));
            assertTrue(site.location.x >= 0 && site.location.x < 31);
            assertTrue(site.location.y >= 0 && site.location.y < 31);
            if (site.kind != FortificationPlanner.Kind.WATER) {
                assertTrue(site.location.x > 0 && site.location.x < 30);
                assertTrue(site.location.y > 0 && site.location.y < 30);
            }
        }
        assertEquals(3, lastStage);
    }

    @Test
    public void firstStageProvidesCardinalStunCoverage() {
        List<FortificationPlanner.Site> sites = FortificationPlanner.generateCycle(
                31, 31, FLAGS, new int[]{0, 0, 0}, SPAWNS, 0);
        int count = 0;
        for (FortificationPlanner.Site site : sites) {
            if (site.stage == 0) {
                assertEquals(FortificationPlanner.Kind.STUN, site.kind);
                count++;
            }
        }
        assertTrue(count >= 8);
        assertTrue(count <= 12);
    }

    @Test
    public void waterUsesDiagonalGridAndLeavesCorridors() {
        List<FortificationPlanner.Site> sites = FortificationPlanner.generateCycle(
                31, 31, FLAGS, new int[]{0, 0, 0}, SPAWNS, 0);
        int water = 0;
        for (FortificationPlanner.Site site : sites) {
            if (site.kind != FortificationPlanner.Kind.WATER) continue;
            water++;
            assertTrue(Math.floorMod(site.location.x + site.location.y, 3) == 0
                    || Math.floorMod(site.location.x - site.location.y, 3) == 0);
        }
        assertTrue(water > 0);
    }

    @Test
    public void secondCycleMovesStunShellOutward() {
        List<FortificationPlanner.Site> sites = FortificationPlanner.generateCycle(
                31, 31, new MapLocation[]{new MapLocation(15, 15)}, new int[]{0}, SPAWNS, 1);
        for (FortificationPlanner.Site site : sites) {
            if (site.stage == 4) {
                assertEquals(6, OpeningLayoutPlanner.chebyshev(site.location, new MapLocation(15, 15)));
            }
        }
    }

    @Test
    public void runtimeTrapSitesAreProjectedOffTheWall() {
        for (int index = 0; index < FortificationPlanner.runtimeSiteCount(FLAGS, 0); index++) {
            FortificationPlanner.Site site = FortificationPlanner.runtimeSiteAt(
                    FLAGS, new int[]{0, 0, 0}, 0, 0, index, 31, 31);
            assertTrue(site.location.x > 0 && site.location.x < 30);
            assertTrue(site.location.y > 0 && site.location.y < 30);
        }
    }
}
