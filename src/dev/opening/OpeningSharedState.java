package dev.opening;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/** Typed owner of the persistent opening section of the shared array. */
public final class OpeningSharedState {
    public static final int FIRST_SLOT = 52;
    public static final int LAST_SLOT = 63;

    private static final int META = 52;
    private static final int FLAG_LOCATION_0 = 53;
    private static final int FLAG_STATUS = 56;
    private static final int ECONOMY = 57;
    private static final int STRATEGIC_SPEND = 58;
    private static final int MIN_CRUMBS = 59;
    private static final int FORTIFICATION = 60;
    private static final int CONSTRUCTION_CURSOR = 61;
    private static final int COORDINATOR_ID = 62;
    private static final int COORDINATOR_ROUND = 63;

    private final RobotController controller;

    public OpeningSharedState(RobotController controller) {
        this.controller = controller;
    }

    public int readEliminatedSymmetries() throws GameActionException {
        return controller.readSharedArray(META) & 7;
    }

    public void eliminateSymmetries(int mask) throws GameActionException {
        int value = controller.readSharedArray(META);
        int updated = value | (mask & 7);
        if (updated != value && controller.canWriteSharedArray(META, updated)) {
            controller.writeSharedArray(META, updated);
        }
    }

    public void writeLayoutZoneCount(int count) throws GameActionException {
        int value = controller.readSharedArray(META);
        int updated = (value & ~(3 << 3)) | ((count & 3) << 3);
        writeIfPossible(META, updated);
    }

    public int readLayoutZoneCount() throws GameActionException {
        return (controller.readSharedArray(META) >>> 3) & 3;
    }

    public boolean isSingleZoneForced() throws GameActionException {
        return (controller.readSharedArray(META) & (1 << 5)) != 0;
    }

    public void forceSingleZone() throws GameActionException {
        int value = controller.readSharedArray(META);
        writeIfPossible(META, value | (1 << 5));
    }

    public boolean claimSummary() throws GameActionException {
        int value = controller.readSharedArray(META);
        if ((value & (1 << 6)) != 0) return false;
        writeIfPossible(META, value | (1 << 6));
        return (controller.readSharedArray(META) & (1 << 6)) != 0;
    }

    public MapLocation readFlagLocation(int slot) throws GameActionException {
        checkFlagSlot(slot);
        return decodeLocation(controller.readSharedArray(FLAG_LOCATION_0 + slot));
    }

    public void writeFlagLocation(int slot, MapLocation location) throws GameActionException {
        checkFlagSlot(slot);
        writeIfPossible(FLAG_LOCATION_0 + slot, encodeLocation(location));
    }

    public int readFlagStatus(int slot) throws GameActionException {
        checkFlagSlot(slot);
        return (controller.readSharedArray(FLAG_STATUS) >>> (slot * 3)) & 7;
    }

    public void writeFlagStatus(int slot, int status) throws GameActionException {
        checkFlagSlot(slot);
        int value = controller.readSharedArray(FLAG_STATUS);
        int shift = slot * 3;
        int updated = (value & ~(7 << shift)) | ((status & 7) << shift);
        writeIfPossible(FLAG_STATUS, updated);
    }

    public int readEconomyWord() throws GameActionException {
        return controller.readSharedArray(ECONOMY);
    }

    public void writeEconomyWord(int value) throws GameActionException {
        writeIfPossible(ECONOMY, value & 0xFFFF);
    }

    public int readStrategicSpend() throws GameActionException {
        return controller.readSharedArray(STRATEGIC_SPEND);
    }

    public void addStrategicSpend(int amount) throws GameActionException {
        int value = Math.min(0xFFFF, readStrategicSpend() + Math.max(0, amount));
        writeIfPossible(STRATEGIC_SPEND, value);
    }

    public void recordMinimumCrumbs(int crumbs) throws GameActionException {
        int current = controller.readSharedArray(MIN_CRUMBS);
        int encoded = Math.min(0xFFFF, crumbs + 1);
        if (current == 0 || encoded < current) writeIfPossible(MIN_CRUMBS, encoded);
    }

    public int readMinimumCrumbs() throws GameActionException {
        int value = controller.readSharedArray(MIN_CRUMBS);
        return value == 0 ? -1 : value - 1;
    }

    public int readFortificationWord() throws GameActionException {
        return controller.readSharedArray(FORTIFICATION);
    }

    public void writeFortificationWord(int value) throws GameActionException {
        writeIfPossible(FORTIFICATION, value & 0xFFFF);
    }

    public int readFortificationStage() throws GameActionException {
        return readFortificationWord() & 15;
    }

    public int readFortificationCompleted() throws GameActionException {
        return readFortificationWord() >>> 4;
    }

    public void completeFortificationSite() throws GameActionException {
        int word = readFortificationWord();
        int completed = Math.min(0xFFF, (word >>> 4) + 1);
        writeFortificationWord((completed << 4) | (word & 15));
    }

    public void advanceFortificationStage(int expectedStage) throws GameActionException {
        int word = readFortificationWord();
        if ((word & 15) != expectedStage) return;
        writeFortificationWord((expectedStage + 1) & 15);
        writeConstructionCursor(0);
    }

    public int readConstructionCursor() throws GameActionException {
        return controller.readSharedArray(CONSTRUCTION_CURSOR);
    }

    public void writeConstructionCursor(int value) throws GameActionException {
        writeIfPossible(CONSTRUCTION_CURSOR, value & 0xFFFF);
    }

    public int claimConstructionIndex(int siteCount) throws GameActionException {
        int cursor = readConstructionCursor();
        if (cursor >= siteCount) return -1;
        writeConstructionCursor(cursor + 1);
        return cursor;
    }

    public boolean claimOrRefreshCoordinator(int id, int round) throws GameActionException {
        int owner = controller.readSharedArray(COORDINATOR_ID);
        int heartbeat = controller.readSharedArray(COORDINATOR_ROUND);
        if (owner == 0 || owner == (id & 0xFFFF) || round - heartbeat > 2) {
            writeIfPossible(COORDINATOR_ID, id & 0xFFFF);
            writeIfPossible(COORDINATOR_ROUND, round & 0xFFFF);
            return controller.readSharedArray(COORDINATOR_ID) == (id & 0xFFFF);
        }
        return false;
    }

    public static int encodeLocation(MapLocation location) {
        if (location == null) return 0;
        return location.x * 64 + location.y + 1;
    }

    public static MapLocation decodeLocation(int value) {
        if (value == 0) return null;
        value--;
        return new MapLocation(value / 64, value % 64);
    }

    private void writeIfPossible(int slot, int value) throws GameActionException {
        if (controller.canWriteSharedArray(slot, value)) controller.writeSharedArray(slot, value);
    }

    private static void checkFlagSlot(int slot) {
        if (slot < 0 || slot >= 3) throw new IllegalArgumentException("flag slot: " + slot);
    }
}
