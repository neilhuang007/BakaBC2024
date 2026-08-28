package dev.opening;

import battlecode.common.MapLocation;
import org.junit.Test;

import static org.junit.Assert.*;

public class OpeningPolicySupportTest {
    @Test
    public void phaseReserveChangesAfterSetup() {
        assertEquals(200, EconomyBudget.reserveForRound(1));
        assertEquals(200, EconomyBudget.reserveForRound(200));
        assertEquals(1000, EconomyBudget.reserveForRound(201));
    }

    @Test
    public void damQuotaIsBounded() {
        assertEquals(0, EconomyBudget.damQuota(199));
        assertEquals(0, EconomyBudget.damQuota(200));
        assertEquals(3, EconomyBudget.damQuota(500));
        assertEquals(10, EconomyBudget.damQuota(100000));
    }

    @Test
    public void sharedLocationEncodingRoundTripsAndReservesZero() {
        assertNull(OpeningSharedState.decodeLocation(0));
        MapLocation location = new MapLocation(59, 58);
        assertEquals(location, OpeningSharedState.decodeLocation(
                OpeningSharedState.encodeLocation(location)));
    }

    @Test
    public void symmetryMaskRequiresExactlyOneCandidate() {
        assertEquals(OpeningLayoutPlanner.Symmetry.UNKNOWN,
                SymmetryTracker.confirmedSymmetry(0));
        assertEquals(OpeningLayoutPlanner.Symmetry.ROTATIONAL,
                SymmetryTracker.confirmedSymmetry(
                        SymmetryTracker.HORIZONTAL_BIT | SymmetryTracker.VERTICAL_BIT));
        assertEquals(OpeningLayoutPlanner.Symmetry.HORIZONTAL,
                SymmetryTracker.confirmedSymmetry(
                        SymmetryTracker.ROTATIONAL_BIT | SymmetryTracker.VERTICAL_BIT));
        assertEquals(OpeningLayoutPlanner.Symmetry.VERTICAL,
                SymmetryTracker.confirmedSymmetry(
                        SymmetryTracker.ROTATIONAL_BIT | SymmetryTracker.HORIZONTAL_BIT));
    }

    @Test
    public void placedFlagAtItsLockedLocationCannotBeRecovered() {
        MapLocation locked = new MapLocation(1, 0);
        assertEquals(-1, OpeningManager.selectRecoverableFlagSlot(
                locked,
                new MapLocation[]{locked, new MapLocation(7, 0), new MapLocation(13, 0)},
                new int[]{OpeningManager.FLAG_STATUS_PLACED,
                        OpeningManager.FLAG_STATUS_PLACED,
                        OpeningManager.FLAG_STATUS_PLACED},
                new MapLocation[]{new MapLocation(3, 18), new MapLocation(3, 27), new MapLocation(12, 27)}));
    }

    @Test
    public void displacedFlagKeepsItsOriginalSlot() {
        assertEquals(1, OpeningManager.selectRecoverableFlagSlot(
                new MapLocation(10, 8),
                new MapLocation[]{new MapLocation(1, 0), new MapLocation(9, 7), new MapLocation(13, 0)},
                new int[]{OpeningManager.FLAG_STATUS_PLACED,
                        OpeningManager.FLAG_STATUS_CARRIED,
                        OpeningManager.FLAG_STATUS_PLACED},
                new MapLocation[]{new MapLocation(3, 18), new MapLocation(3, 27), new MapLocation(12, 27)}));
    }

    @Test
    public void stalledTransportFallsBackBeforeTheSetupDeadline() {
        assertFalse(OpeningManager.transportFallbackRequired(40, 11));
        assertTrue(OpeningManager.transportFallbackRequired(41, 11));
        assertTrue(OpeningManager.transportFallbackRequired(150, 149));
    }

    @Test
    public void enemyFlagCarriersCannotReenterTheOpeningLifecycle() {
        assertTrue(OpeningManager.isSetupFlagLifecycleRound(200));
        assertFalse(OpeningManager.isSetupFlagLifecycleRound(201));
        assertFalse(OpeningManager.isSetupFlagLifecycleRound(1600));
    }

    @Test
    public void wallDistanceRecognizesEveryMapEdge() {
        assertEquals(0, OpeningManager.distanceToWall(new MapLocation(0, 8), 31, 31));
        assertEquals(0, OpeningManager.distanceToWall(new MapLocation(30, 8), 31, 31));
        assertEquals(0, OpeningManager.distanceToWall(new MapLocation(8, 0), 31, 31));
        assertEquals(0, OpeningManager.distanceToWall(new MapLocation(8, 30), 31, 31));
        assertEquals(5, OpeningManager.distanceToWall(new MapLocation(5, 9), 31, 31));
    }

    @Test
    public void surplusAddsOneBuilderCohortOnlyDuringNormalPlay() {
        assertFalse(OpeningManager.surplusConstructionEnabled(200, 25000, 1));
        assertFalse(OpeningManager.surplusConstructionEnabled(201, 4999, 1));
        assertTrue(OpeningManager.surplusConstructionEnabled(201, 5000, 1));
        assertFalse(OpeningManager.surplusConstructionEnabled(201, 14999, 2));
        assertTrue(OpeningManager.surplusConstructionEnabled(201, 15000, 2));
        assertFalse(OpeningManager.surplusConstructionEnabled(201, 24999, 3));
        assertTrue(OpeningManager.surplusConstructionEnabled(201, 25000, 3));
        assertFalse(OpeningManager.surplusConstructionEnabled(201, 50000, 4));
    }

    @Test
    public void exhaustedConstructionQueueIsReissuedAfterAStall() {
        assertFalse(OpeningManager.shouldRequeueConstruction(11, 12, 10, 100, 50));
        assertFalse(OpeningManager.shouldRequeueConstruction(12, 12, 12, 100, 50));
        assertFalse(OpeningManager.shouldRequeueConstruction(12, 12, 10, 89, 50));
        assertTrue(OpeningManager.shouldRequeueConstruction(12, 12, 10, 90, 50));
    }

    @Test
    public void lateStagesInterleaveInnerMaintenanceAndOuterExpansion() {
        assertEquals(0, OpeningManager.constructionCycleForStage(0));
        assertEquals(1, OpeningManager.constructionCycleForStage(5));
        assertEquals(0, OpeningManager.constructionCycleForStage(9));
        assertEquals(0, OpeningManager.constructionLayerForStage(9));
        assertEquals(3, OpeningManager.constructionLayerForStage(12));
        assertEquals(2, OpeningManager.constructionCycleForStage(13));
        assertEquals(2, OpeningManager.constructionLayerForStage(15));
    }
}
