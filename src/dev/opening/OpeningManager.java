package dev.opening;

import battlecode.common.Direction;
import battlecode.common.FlagInfo;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.TrapType;
import dev.knowledge.TurnContext;

/** Runtime coordinator for setup flag placement and layered fortification. */
public final class OpeningManager {
    private static final int CORNER_ZONE_RADIUS_SQUARED = 225;
    private static final int FLAG_RESERVATION_SEARCH_RADIUS = 5;
    private static final int SURPLUS_CONSTRUCTION_CRUMBS = 5000;
    private static final int ABUNDANT_CONSTRUCTION_CRUMBS = 15000;
    private static final int OVERFLOW_CONSTRUCTION_CRUMBS = 25000;
    private static final int CONSTRUCTION_STALL_ROUNDS = 40;
    public static final int FLAG_STATUS_UNKNOWN = 0;
    public static final int FLAG_STATUS_CARRIED = 1;
    public static final int FLAG_STATUS_RESERVED = 2;
    public static final int FLAG_STATUS_PLACED = 3;

    private final RobotController controller;
    private final int stableId;
    private final boolean builder;
    private final MapLocation[] spawnCenters;
    private final OpeningLayoutPlanner layoutPlanner;
    private final OpeningSharedState shared;
    private final SymmetryTracker symmetryTracker;
    private final EconomyBudget economy;

    private OpeningLayoutPlanner.Symmetry symmetry = OpeningLayoutPlanner.Symmetry.UNKNOWN;
    private OpeningLayoutPlanner.Symmetry layoutSymmetry = OpeningLayoutPlanner.Symmetry.UNKNOWN;
    private OpeningLayoutPlanner.Layout layout;
    private int carrierSlot = -1;
    private Boolean secondaryCommitted;
    private boolean carrierFallback;
    private MapLocation carrierProgressTarget;
    private int carrierBestDistance = Integer.MAX_VALUE;
    private int carrierLastProgressRound;
    private boolean coordinator;

    private FortificationPlanner.Site assignedSite;
    private int assignedGlobalStage = -1;
    private int assignedRound;
    private MapLocation constructionTarget;
    private int observedFortificationWord = -1;
    private int fortificationProgressRound;

    public OpeningManager(RobotController controller, int stableId) {
        this.controller = controller;
        this.stableId = stableId;
        this.builder = Math.floorMod(stableId, 5) == 0;
        this.spawnCenters = OpeningLayoutPlanner.computeSpawnCenters(controller.getAllySpawnLocations());
        this.layoutPlanner = new OpeningLayoutPlanner(
                controller.getMapWidth(), controller.getMapHeight(), spawnCenters);
        this.shared = new OpeningSharedState(controller);
        this.symmetryTracker = new SymmetryTracker(controller.getMapWidth(), controller.getMapHeight());
        this.economy = new EconomyBudget(controller, shared);
        this.symmetry = layoutPlanner.inferSymmetryFromSpawns();
        this.layoutSymmetry = symmetry;
        this.layout = layoutPlanner.plan(symmetry);
    }

    public void beginTurn(TurnContext turn) throws GameActionException {
        symmetryTracker.observe(turn.mapInfos, shared);
        coordinator = shared.claimOrRefreshCoordinator(stableId, turn.round);
        economy.update(coordinator);
        OpeningLayoutPlanner.Symmetry confirmed =
                SymmetryTracker.confirmedSymmetry(shared.readEliminatedSymmetries());
        if (confirmed != OpeningLayoutPlanner.Symmetry.UNKNOWN) symmetry = confirmed;
        if (turn.round >= 70 && symmetry == OpeningLayoutPlanner.Symmetry.UNKNOWN) {
            shared.forceSingleZone();
        }
        boolean forceSingle = shared.isSingleZoneForced();
        OpeningLayoutPlanner.Symmetry desiredSymmetry = forceSingle
                ? OpeningLayoutPlanner.Symmetry.UNKNOWN : symmetry;
        if (layout == null || desiredSymmetry != layoutSymmetry) {
            layout = layoutPlanner.plan(desiredSymmetry);
            layoutSymmetry = desiredSymmetry;
        }
        if (coordinator) shared.writeLayoutZoneCount(forceSingle ? 1 : layout.zoneCount);

        if (isSetupFlagLifecycleRound(turn.round) && turn.hasFlag && carrierSlot < 0) {
            carrierSlot = recoverableFlagSlot(turn.location);
            if (carrierSlot < 0) carrierSlot = layoutPlanner.nearestSpawnCenter(turn.location);
            shared.writeFlagStatus(carrierSlot, FLAG_STATUS_CARRIED);
            resetCarrierProgress(turn.round);
        }
        if (isSetupFlagLifecycleRound(turn.round) && turn.hasFlag) updateCarrierProgress(turn);
        prepareConstruction(turn, forceSingle);
        if (turn.round == GameConstants.SETUP_ROUNDS && coordinator && shared.claimSummary()) {
            printSummary();
        }
        if (turn.round == 2000 && coordinator) printDefenseSummary(turn);
    }

    public boolean tryFlagAction(TurnContext turn) throws GameActionException {
        if (turn.round > GameConstants.SETUP_ROUNDS) return false;
        if (!turn.hasFlag) {
            if (turn.round >= 198) return false;
            FlagInfo best = null;
            int bestSlot = -1;
            for (FlagInfo flag : turn.allyFlags) {
                if (flag.isPickedUp() || !turn.location.equals(flag.getLocation())
                        || !controller.canPickupFlag(flag.getLocation())) continue;
                int slot = recoverableFlagSlot(flag.getLocation());
                if (slot < 0) continue;
                if (best == null || flag.getID() < best.getID()) {
                    best = flag;
                    bestSlot = slot;
                }
            }
            if (best != null) {
                carrierSlot = bestSlot;
                controller.pickupFlag(best.getLocation());
                shared.writeFlagStatus(carrierSlot, FLAG_STATUS_CARRIED);
                resetCarrierProgress(turn.round);
                System.out.println("OPENING_PICKUP|slot=" + carrierSlot + "|round=" + turn.round
                        + "|loc=" + turn.location.x + "," + turn.location.y);
                return true;
            }
            return false;
        }

        if (carrierSlot < 0) carrierSlot = layoutPlanner.nearestSpawnCenter(turn.location);
        MapLocation desired = carrierDestination(turn);
        MapLocation reserved = shared.readFlagLocation(carrierSlot);
        if (reserved == null) {
            reserved = findLegalReservation(desired);
            if (reserved != null) {
                shared.writeFlagLocation(carrierSlot, reserved);
                shared.writeFlagStatus(carrierSlot, FLAG_STATUS_RESERVED);
            }
        }

        if (reserved != null && controller.canDropFlag(reserved)) {
            controller.dropFlag(reserved);
            shared.writeFlagLocation(carrierSlot, reserved);
            shared.writeFlagStatus(carrierSlot, FLAG_STATUS_PLACED);
            System.out.println("OPENING_DROP|slot=" + carrierSlot + "|round=" + turn.round
                    + "|loc=" + reserved.x + "," + reserved.y);
            carrierSlot = -1;
            resetCarrierProgress(turn.round);
            return true;
        }
        return false;
    }

    public MapLocation movementTarget(TurnContext turn) throws GameActionException {
        if (controller.hasFlag() && turn.round <= GameConstants.SETUP_ROUNDS) {
            MapLocation reserved = carrierSlot < 0 ? null : shared.readFlagLocation(carrierSlot);
            return reserved != null ? reserved : carrierDestination(turn);
        }
        return constructionTarget;
    }

    public boolean shouldUseConstructionObjective(TurnContext turn) {
        if (!isEligibleBuilder(turn) || constructionTarget == null || controller.hasFlag()) return false;
        return turn.enemies.length == 0 && turn.enemyFlags.length == 0;
    }

    public boolean tryConstruct(TurnContext turn) throws GameActionException {
        if (!isEligibleBuilder(turn) || turn.hasFlag || turn.enemies.length > 0 || turn.enemyFlags.length > 0
                || !controller.isActionReady() || !allFlagsPlaced()) return false;
        int stage = shared.readFortificationStage();
        if (stage == 4) return tryBuildDamTrap(turn);
        if (assignedSite == null) return false;
        if (!controller.onTheMap(assignedSite.location)) {
            finishAssignedSite();
            return false;
        }
        if (!controller.canSenseLocation(assignedSite.location)
                || turn.location.distanceSquaredTo(assignedSite.location) > GameConstants.INTERACT_RADIUS_SQUARED) {
            if (turn.round - assignedRound > 30) finishAssignedSite();
            return false;
        }

        MapInfo info = controller.senseMapInfo(assignedSite.location);
        if (assignedSite.kind == FortificationPlanner.Kind.WATER) {
            if (info.isWater() || info.isWall() || info.isDam() || info.isSpawnZone()
                    || info.getTrapType() != TrapType.NONE) {
                finishAssignedSite();
                return false;
            }
            if (economy.canSpendStrategic(20) && controller.canDig(assignedSite.location)) {
                controller.dig(assignedSite.location);
                economy.recordStrategicSpend(20);
                finishAssignedSite();
                return true;
            }
            return false;
        }

        TrapType trap = assignedSite.kind == FortificationPlanner.Kind.STUN
                ? TrapType.STUN : TrapType.EXPLOSIVE;
        if (info.getTrapType() != TrapType.NONE || info.isWall() || info.isWater()
                || info.isDam() || info.isSpawnZone()) {
            finishAssignedSite();
            return false;
        }
        if (economy.canSpendStrategic(trap.buildCost) && controller.canBuild(trap, assignedSite.location)) {
            controller.build(trap, assignedSite.location);
            economy.recordStrategicSpend(trap.buildCost);
            finishAssignedSite();
            return true;
        }
        return false;
    }

    public boolean canSpendTactical(int cost) {
        return economy.canSpendTactical(cost);
    }

    public String indicatorCode() throws GameActionException {
        String sym = symmetry == OpeningLayoutPlanner.Symmetry.UNKNOWN
                ? "U" : symmetry.name().substring(0, 1);
        return "|O" + sym + "Z" + shared.readLayoutZoneCount()
                + "S" + shared.readFortificationStage();
    }

    private MapLocation carrierDestination(TurnContext turn) throws GameActionException {
        if (carrierSlot < 0) return turn.location;
        if (carrierFallback) return spawnCenters[carrierSlot];
        if (shared.isSingleZoneForced() || symmetry == OpeningLayoutPlanner.Symmetry.UNKNOWN) {
            return layout.conservativeTargets[carrierSlot];
        }
        if (layout.secondary[carrierSlot]) {
            if (secondaryCommitted == null) {
                MapLocation target = layout.primaryTargets[carrierSlot];
                int required = 2 * OpeningLayoutPlanner.chebyshev(turn.location, target) + 12;
                secondaryCommitted = required <= 198 - turn.round;
                if (!secondaryCommitted) shared.forceSingleZone();
            }
            if (!secondaryCommitted) return layout.conservativeTargets[carrierSlot];
        }
        return layout.primaryTargets[carrierSlot];
    }

    private void updateCarrierProgress(TurnContext turn) throws GameActionException {
        if (carrierSlot < 0 || carrierFallback) return;
        MapLocation destination = carrierDestination(turn);
        if (!destination.equals(carrierProgressTarget)) {
            carrierProgressTarget = destination;
            carrierBestDistance = OpeningLayoutPlanner.chebyshev(turn.location, destination);
            carrierLastProgressRound = turn.round;
            return;
        }
        int distance = OpeningLayoutPlanner.chebyshev(turn.location, destination);
        if (distance < carrierBestDistance) {
            carrierBestDistance = distance;
            carrierLastProgressRound = turn.round;
        }
        if (!transportFallbackRequired(turn.round, carrierLastProgressRound)) return;

        carrierFallback = true;
        shared.writeFlagLocation(carrierSlot, null);
        shared.writeFlagStatus(carrierSlot, FLAG_STATUS_CARRIED);
        System.out.println("OPENING_FALLBACK|slot=" + carrierSlot + "|round=" + turn.round
                + "|from=" + turn.location.x + "," + turn.location.y
                + "|spawn=" + spawnCenters[carrierSlot].x + "," + spawnCenters[carrierSlot].y);
    }

    private void resetCarrierProgress(int round) {
        carrierFallback = false;
        carrierProgressTarget = null;
        carrierBestDistance = Integer.MAX_VALUE;
        carrierLastProgressRound = round;
    }

    static boolean transportFallbackRequired(int round, int lastProgressRound) {
        return round >= 150 || round - lastProgressRound >= 30;
    }

    static boolean isSetupFlagLifecycleRound(int round) {
        return round <= GameConstants.SETUP_ROUNDS;
    }

    private MapLocation findLegalReservation(MapLocation center) throws GameActionException {
        MapLocation best = null;
        int bestWallDistance = Integer.MAX_VALUE;
        int bestRadius = Integer.MAX_VALUE;
        for (int radius = 0; radius <= FLAG_RESERVATION_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                    MapLocation candidate = new MapLocation(center.x + dx, center.y + dy);
                    if (!controller.onTheMap(candidate) || !controller.canSenseLocation(candidate)) continue;
                    MapInfo info = controller.senseMapInfo(candidate);
                    if (!info.isPassable() || info.isWater() || info.isDam() || info.isSpawnZone()) continue;
                    if (!spacedFromOtherFlags(candidate)) continue;
                    if (!controller.senseLegalStartingFlagPlacement(candidate)) continue;
                    int wallDistance = distanceToWall(candidate,
                            controller.getMapWidth(), controller.getMapHeight());
                    if (wallDistance < bestWallDistance
                            || (wallDistance == bestWallDistance && radius < bestRadius)
                            || (wallDistance == bestWallDistance && radius == bestRadius
                            && compareLocation(candidate, best) < 0)) {
                        best = candidate;
                        bestWallDistance = wallDistance;
                        bestRadius = radius;
                    }
                }
            }
            if (bestWallDistance == 0) return best;
        }
        return best;
    }

    static int distanceToWall(MapLocation location, int width, int height) {
        return Math.min(Math.min(location.x, width - 1 - location.x),
                Math.min(location.y, height - 1 - location.y));
    }

    private static int compareLocation(MapLocation a, MapLocation b) {
        if (b == null) return -1;
        if (a.x != b.x) return a.x - b.x;
        return a.y - b.y;
    }

    private boolean spacedFromOtherFlags(MapLocation candidate) throws GameActionException {
        for (int slot = 0; slot < 3; slot++) {
            if (slot == carrierSlot) continue;
            MapLocation other = shared.readFlagLocation(slot);
            if (other == null) other = layout.primaryTargets[slot];
            if (candidate.distanceSquaredTo(other) < GameConstants.MIN_FLAG_SPACING_SQUARED) return false;
        }
        return true;
    }

    private void prepareConstruction(TurnContext turn, boolean forceSingle) throws GameActionException {
        constructionTarget = null;
        if (!isEligibleBuilder(turn) || turn.hasFlag
                || !allFlagsPlaced()
                || (symmetry == OpeningLayoutPlanner.Symmetry.UNKNOWN && !forceSingle)) {
            assignedSite = null;
            assignedGlobalStage = -1;
            return;
        }
        int stage = shared.readFortificationStage();
        if (assignedSite != null && assignedGlobalStage != stage) assignedSite = null;
        if (assignedSite != null && turn.round - assignedRound > 30) finishAssignedSite();
        if (stage == 4) {
            int quota = EconomyBudget.damQuota(economy.forecast());
            if (turn.round > GameConstants.SETUP_ROUNDS || shared.readFortificationCompleted() >= quota) {
                shared.advanceFortificationStage(4);
                assignedSite = null;
                return;
            }
            constructionTarget = new MapLocation(controller.getMapWidth() / 2, controller.getMapHeight() / 2);
            return;
        }

        int cycle = constructionCycleForStage(stage);
        int stageWithinCycle = constructionLayerForStage(stage);
        MapLocation[] flagLocations = plannedFlagLocations(forceSingle);
        int[] zones = plannedZones(forceSingle);
        int siteCount = FortificationPlanner.runtimeSiteCount(flagLocations, stageWithinCycle);
        maintainConstructionQueue(turn.round, siteCount);
        if (shared.readFortificationCompleted() >= siteCount) {
            shared.advanceFortificationStage(stage);
            assignedSite = null;
            return;
        }
        if (assignedSite == null) {
            int index = shared.claimConstructionIndex(siteCount);
            if (index >= 0) {
                assignedSite = FortificationPlanner.runtimeSiteAt(
                        flagLocations, zones, cycle, stageWithinCycle, index,
                        controller.getMapWidth(), controller.getMapHeight());
                assignedGlobalStage = stage;
                assignedRound = turn.round;
            }
        }
        if (assignedSite != null) constructionTarget = assignedSite.location;
    }

    private void maintainConstructionQueue(int round, int siteCount) throws GameActionException {
        if (!coordinator) return;
        int word = shared.readFortificationWord();
        if (word != observedFortificationWord) {
            observedFortificationWord = word;
            fortificationProgressRound = round;
            return;
        }
        int completed = shared.readFortificationCompleted();
        int cursor = shared.readConstructionCursor();
        if (shouldRequeueConstruction(cursor, siteCount, completed,
                round, fortificationProgressRound)) {
            shared.writeConstructionCursor(0);
            fortificationProgressRound = round;
        }
    }

    static boolean shouldRequeueConstruction(
            int cursor,
            int siteCount,
            int completed,
            int round,
            int lastProgressRound
    ) {
        return cursor >= siteCount && completed < siteCount
                && round - lastProgressRound >= CONSTRUCTION_STALL_ROUNDS;
    }

    static int constructionCycleForStage(int stage) {
        if (stage < 4) return 0;
        if (stage <= 8) return 1;
        if (stage <= 12) return 0;
        return 2;
    }

    static int constructionLayerForStage(int stage) {
        if (stage < 4) return stage;
        if (stage <= 8) return stage - 5;
        if (stage <= 12) return stage - 9;
        return stage - 13;
    }

    private MapLocation[] plannedFlagLocations(boolean forceSingle) throws GameActionException {
        MapLocation[] result = new MapLocation[3];
        for (int i = 0; i < 3; i++) {
            result[i] = shared.readFlagLocation(i);
            if (result[i] == null) {
                result[i] = forceSingle ? layout.conservativeTargets[i] : layout.primaryTargets[i];
            }
        }
        return result;
    }

    private int[] plannedZones(boolean forceSingle) {
        int[] zones = new int[3];
        if (!forceSingle && layout.zoneCount == 2) {
            for (int i = 0; i < 3; i++) zones[i] = layout.secondary[i] ? 1 : 0;
        }
        return zones;
    }

    private boolean tryBuildDamTrap(TurnContext turn) throws GameActionException {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (MapInfo dam : turn.mapInfos) {
            if (!dam.isDam()) continue;
            for (Direction direction : Direction.allDirections()) {
                if (direction == Direction.CENTER) continue;
                MapLocation candidate = dam.getMapLocation().add(direction);
                if (!controller.onTheMap(candidate)) continue;
                int distance = turn.location.distanceSquaredTo(candidate);
                if (distance < bestDistance && controller.canBuild(TrapType.STUN, candidate)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        if (best != null && economy.canSpendStrategic(TrapType.STUN.buildCost)) {
            controller.build(TrapType.STUN, best);
            economy.recordStrategicSpend(TrapType.STUN.buildCost);
            shared.completeFortificationSite();
            return true;
        }
        return false;
    }

    private int recoverableFlagSlot(MapLocation location) throws GameActionException {
        MapLocation[] locations = new MapLocation[3];
        int[] statuses = new int[3];
        for (int slot = 0; slot < 3; slot++) {
            locations[slot] = shared.readFlagLocation(slot);
            statuses[slot] = shared.readFlagStatus(slot);
        }
        return selectRecoverableFlagSlot(location, locations, statuses, spawnCenters);
    }

    static int selectRecoverableFlagSlot(
            MapLocation flag,
            MapLocation[] locations,
            int[] statuses,
            MapLocation[] spawnCenters
    ) {
        for (int slot = 0; slot < 3; slot++) {
            if (statuses[slot] == FLAG_STATUS_PLACED
                    && flag.equals(locations[slot])) return -1;
        }

        int spawnSlot = nearest(flag, spawnCenters);
        if (statuses[spawnSlot] != FLAG_STATUS_PLACED
                && flag.distanceSquaredTo(spawnCenters[spawnSlot]) <= 2) return spawnSlot;

        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int slot = 0; slot < 3; slot++) {
            if (locations[slot] == null) continue;
            int distance = flag.distanceSquaredTo(locations[slot]);
            if (distance < bestDistance || (distance == bestDistance && slot < best)) {
                best = slot;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean allFlagsPlaced() throws GameActionException {
        for (int slot = 0; slot < 3; slot++) {
            if (shared.readFlagStatus(slot) != FLAG_STATUS_PLACED
                    || shared.readFlagLocation(slot) == null) return false;
        }
        return true;
    }

    private static int nearest(MapLocation location, MapLocation[] candidates) {
        int best = 0;
        int bestDistance = location.distanceSquaredTo(candidates[0]);
        for (int i = 1; i < candidates.length; i++) {
            int distance = location.distanceSquaredTo(candidates[i]);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void finishAssignedSite() throws GameActionException {
        shared.completeFortificationSite();
        assignedSite = null;
        assignedGlobalStage = -1;
        constructionTarget = null;
    }

    private void printSummary() throws GameActionException {
        StringBuilder locations = new StringBuilder();
        StringBuilder statuses = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                locations.append(';');
                statuses.append(',');
            }
            MapLocation location = shared.readFlagLocation(i);
            locations.append(location == null ? "-" : location.x + "," + location.y);
            statuses.append(shared.readFlagStatus(i));
        }
        System.out.println("OPENING_SUMMARY|sym=" + symmetry
                + "|zones=" + shared.readLayoutZoneCount()
                + "|actualZones=" + actualZoneCount()
                + "|flags=" + locations
                + "|flagStatus=" + statuses
                + "|fortStage=" + shared.readFortificationStage()
                + "|fortDone=" + shared.readFortificationCompleted()
                + "|fortCursor=" + shared.readConstructionCursor()
                + "|spend=" + shared.readStrategicSpend()
                + "|minCrumbs=" + shared.readMinimumCrumbs());
    }

    private void printDefenseSummary(TurnContext turn) throws GameActionException {
        System.out.println("DEFENSE_SUMMARY|round=" + turn.round
                + "|stage=" + shared.readFortificationStage()
                + "|done=" + shared.readFortificationCompleted()
                + "|cursor=" + shared.readConstructionCursor()
                + "|spend=" + shared.readStrategicSpend()
                + "|crumbs=" + controller.getCrumbs());
    }

    private int actualZoneCount() throws GameActionException {
        MapLocation[] flags = new MapLocation[3];
        for (int i = 0; i < flags.length; i++) flags[i] = shared.readFlagLocation(i);
        boolean[] seen = new boolean[3];
        int zones = 0;
        for (int i = 0; i < flags.length; i++) {
            if (seen[i] || flags[i] == null) continue;
            zones++;
            seen[i] = true;
            boolean changed;
            do {
                changed = false;
                for (int j = 0; j < flags.length; j++) {
                    if (seen[j] || flags[j] == null) continue;
                    for (int k = 0; k < flags.length; k++) {
                        if (seen[k] && flags[j].distanceSquaredTo(flags[k])
                                <= CORNER_ZONE_RADIUS_SQUARED) {
                            seen[j] = true;
                            changed = true;
                            break;
                        }
                    }
                }
            } while (changed);
        }
        return zones;
    }

    private boolean isEligibleBuilder(TurnContext turn) {
        if (builder || coordinator) return true;
        if (turn.round > GameConstants.SETUP_ROUNDS) {
            return surplusConstructionEnabled(turn.round, controller.getCrumbs(), stableId);
        }
        try {
            for (int i = 0; i < 3; i++) {
                MapLocation flag = shared.readFlagLocation(i);
                if (flag != null && turn.location.distanceSquaredTo(flag) <= 100) return true;
            }
        } catch (GameActionException ignored) {
            return false;
        }
        return false;
    }

    static boolean surplusConstructionEnabled(int round, int crumbs, int stableId) {
        if (round <= GameConstants.SETUP_ROUNDS) return false;
        int cohort = Math.floorMod(stableId, 5);
        return (cohort == 1 && crumbs >= SURPLUS_CONSTRUCTION_CRUMBS)
                || (cohort == 2 && crumbs >= ABUNDANT_CONSTRUCTION_CRUMBS)
                || (cohort == 3 && crumbs >= OVERFLOW_CONSTRUCTION_CRUMBS);
    }
}
