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
}
