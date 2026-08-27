package baseline.entities;

import battlecode.common.Direction;
import battlecode.common.FlagInfo;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.GlobalUpgrade;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.SkillType;
import battlecode.common.TrapType;
import baseline.GameState;
import baseline.GlobalDecider;
import baseline.RobotPlayer;
import baseline.knowledge.TurnContext;
import baseline.navigation.BaselineNavigator;
import baseline.strategy.Objective;
import baseline.strategy.ObjectiveType;
import baseline.strategy.PolicySupport;

public final class LocalBot extends Bot {
    private static final int FALLBACK_STUCK_ROUNDS = 12;

    private final RobotController controller;
    private final GlobalDecider decider;
    private final int stableId;
    private final MapLocation[] spawnLocations;
    private final BaselineNavigator navigator;

    private TurnContext context;
    private Objective objective;
    private int explorationEpoch;
    private int broadcastOffset;
    private MapLocation progressTarget;
    private int previousDistance = Integer.MAX_VALUE;
    private int noProgressRounds;
    private boolean builtSetupTrap;

    public LocalBot(GlobalDecider decider, int stableId) {
        super(decider.getController().getID(), RobotPlayer.TEAM_THIS);
        this.controller = decider.getController();
        this.decider = decider;
        this.stableId = stableId;
        this.spawnLocations = controller.getAllySpawnLocations();
        this.navigator = new BaselineNavigator(controller, stableId);
    }

    public void onTick() throws GameActionException {
        if (!controller.isSpawned()) {
            health = 0;
            trySpawn();
            if (!controller.isSpawned()) {
                controller.setIndicatorString("LOCAL|JAILED|SPAWN");
                return;
            }
            navigator.reset();
        }

        context = TurnContext.capture(controller);
        updateSelf(context);
        tryBuyGlobalUpgrade();

        objective = chooseObjective(context);
        objective = advanceExpiredFallback(objective, context);

        boolean acted = tryPriorityAction(context.enemyFlags, context.enemies, context.teammates);
        if (!acted && decider.getState() == GameState.PREPARE) {
            acted = tryBuildSetupTrap();
        }
        if (!acted) {
            tryFillTowardObjective();
        }

        Direction step = navigator.nextStep(objective.target, context, objective.cautious);
        if (step != Direction.CENTER && controller.canMove(step)) {
            controller.move(step);
            x = controller.getLocation().x;
            y = controller.getLocation().y;
        }

        if (controller.isActionReady()) {
            tryPriorityAction(
                    controller.senseNearbyFlags(-1, controller.getTeam().opponent()),
                    controller.senseNearbyRobots(-1, controller.getTeam().opponent()),
                    controller.senseNearbyRobots(-1, controller.getTeam())
            );
        }
        setIndicator();
    }

    private void trySpawn() throws GameActionException {
        int[] indexes = PolicySupport.cyclicIndexes(spawnLocations.length, stableId);
        MapLocation target = objective == null ? null : objective.target;
        int bestIndex = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int rank = 0; rank < indexes.length; rank++) {
            int index = indexes[rank];
            MapLocation spawn = spawnLocations[index];
            if (!controller.canSpawn(spawn)) continue;
            int score = rank;
            if (target != null) score += spawn.distanceSquaredTo(target) * 100;
            if (score < bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        if (bestIndex >= 0) controller.spawn(spawnLocations[bestIndex]);
    }

    private void updateSelf(TurnContext turn) {
        x = turn.location.x;
        y = turn.location.y;
        health = controller.getHealth();
        hasFlag = turn.hasFlag;
        attackLevel = controller.getLevel(SkillType.ATTACK);
        healLevel = controller.getLevel(SkillType.HEAL);
        buildLevel = controller.getLevel(SkillType.BUILD);
    }

    private Objective chooseObjective(TurnContext turn) {
        if (turn.hasFlag) {
            return new Objective(ObjectiveType.RETURN, nearestSpawn(turn.location), true);
        }

        RobotInfo enemyCarrier = nearestCarrier(turn.enemies, turn.location);
        if (enemyCarrier != null) {
            return new Objective(ObjectiveType.INTERCEPT, enemyCarrier.location, false);
        }

        FlagInfo enemyFlag = nearestGroundFlag(turn.enemyFlags, turn.location);
        if (enemyFlag != null) {
            return new Objective(ObjectiveType.CAPTURE, enemyFlag.getLocation(), false);
        }

        if (turn.enemies.length > 0) {
            RobotInfo threat = enemyThreateningFlag(turn);
            boolean retreat = health <= controller.getAttackDamage() * 2
                    || turn.enemies.length > turn.teammates.length + 2;
            if (retreat) {
                return new Objective(ObjectiveType.RETREAT, nearestSpawn(turn.location), true);
            }
            if (threat != null) {
                return new Objective(ObjectiveType.DEFEND, threat.location, false);
            }
            RobotInfo enemy = nearestRobot(turn.enemies, turn.location);
            return new Objective(ObjectiveType.ENGAGE, enemy.location, false);
        }

        if (decider.getState() == GameState.PREPARE) {
            MapLocation crumb = nearestLocation(turn.crumbs, turn.location);
            if (crumb != null) return new Objective(ObjectiveType.CRUMBS, crumb, false);
        } else if (turn.broadcastEnemyFlags.length > 0) {
            int index = Math.floorMod(stableId + broadcastOffset, turn.broadcastEnemyFlags.length);
            return new Objective(ObjectiveType.BROADCAST, turn.broadcastEnemyFlags[index], false);
        }

        return new Objective(
                ObjectiveType.EXPLORE,
                PolicySupport.explorationTarget(
                        controller.getMapWidth(),
                        controller.getMapHeight(),
                        stableId,
                        explorationEpoch
                ),
                false
        );
    }

    private Objective advanceExpiredFallback(Objective selected, TurnContext turn) {
        if (selected.target == null) return selected;
        if (!selected.target.equals(progressTarget)) {
            progressTarget = selected.target;
            previousDistance = turn.location.distanceSquaredTo(selected.target);
            noProgressRounds = 0;
            return selected;
        }

        int distance = turn.location.distanceSquaredTo(selected.target);
        if (distance < previousDistance) noProgressRounds = 0;
        else noProgressRounds++;
        previousDistance = distance;

        boolean fallback = selected.type == ObjectiveType.BROADCAST
                || selected.type == ObjectiveType.EXPLORE;
        if (fallback && (distance <= 4 || noProgressRounds >= FALLBACK_STUCK_ROUNDS)) {
            if (selected.type == ObjectiveType.BROADCAST) broadcastOffset++;
            explorationEpoch++;
            progressTarget = null;
            noProgressRounds = 0;
            navigator.reset();
            return chooseObjective(turn);
        }
        return selected;
    }

    private boolean tryPriorityAction(
            FlagInfo[] enemyFlags,
            RobotInfo[] enemies,
            RobotInfo[] teammates
    ) throws GameActionException {
        if (!controller.isActionReady()) return false;
        MapLocation here = controller.getLocation();

        FlagInfo flag = nearestGroundFlag(enemyFlags, here);
        if (flag != null && controller.canPickupFlag(flag.getLocation())) {
            controller.pickupFlag(flag.getLocation());
            hasFlag = true;
            return true;
        }

        RobotInfo attack = PolicySupport.selectAttackTarget(
                enemies,
                here,
                controller.getAttackDamage()
        );
        if (attack != null && controller.canAttack(attack.location)) {
            controller.attack(attack.location);
            return true;
        }

        RobotInfo heal = PolicySupport.selectHealTarget(teammates, here);
        if (heal != null && controller.canHeal(heal.location)) {
            controller.heal(heal.location);
            return true;
        }
        return false;
    }

    private boolean tryBuildSetupTrap() throws GameActionException {
        if (builtSetupTrap
                || context.round < 170
                || Math.floorMod(stableId, 5) != 0
                || !controller.isActionReady()) {
            return false;
        }

        MapLocation best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < context.mapInfos.length; i++) {
            MapInfo candidateInfo = context.mapInfos[i];
            MapLocation candidate = candidateInfo.getMapLocation();
            if (!candidateInfo.isPassable() || !controller.canBuild(TrapType.STUN, candidate)) continue;
            int damDistance = nearestDamDistance(candidate);
            if (damDistance == Integer.MAX_VALUE) continue;
            int score = damDistance * 10000 + candidate.x * 100 + candidate.y;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best != null) {
            controller.build(TrapType.STUN, best);
            builtSetupTrap = true;
            return true;
        }
        return false;
    }

    private int nearestDamDistance(MapLocation location) {
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < context.mapInfos.length; i++) {
            if (!context.mapInfos[i].isDam()) continue;
            int distance = location.distanceSquaredTo(context.mapInfos[i].getMapLocation());
            if (distance < best) best = distance;
        }
        return best;
    }

    private boolean tryFillTowardObjective() throws GameActionException {
        if (!controller.isActionReady() || objective == null || objective.target == null) return false;
        Direction direct = controller.getLocation().directionTo(objective.target);
        if (direct == Direction.CENTER) return false;
        MapLocation next = controller.getLocation().add(direct);
        MapInfo info = context.mapInfoAt(next);
        if (info != null && info.isWater() && controller.canFill(next)) {
            controller.fill(next);
            return true;
        }
        return false;
    }

    private void tryBuyGlobalUpgrade() throws GameActionException {
        if (context.round < GameConstants.GLOBAL_UPGRADE_ROUNDS
                || context.round % 10 != Math.floorMod(stableId, 10)) {
            return;
        }
        GlobalUpgrade[] order = {
                GlobalUpgrade.CAPTURING,
                GlobalUpgrade.ATTACK,
                GlobalUpgrade.HEALING
        };
        for (int i = 0; i < order.length; i++) {
            if (controller.canBuyGlobal(order[i])) {
                controller.buyGlobal(order[i]);
                return;
            }
        }
    }

    private RobotInfo enemyThreateningFlag(TurnContext turn) {
        RobotInfo best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < turn.allyFlags.length; i++) {
            MapLocation flag = turn.allyFlags[i].getLocation();
            for (int j = 0; j < turn.enemies.length; j++) {
                int distance = flag.distanceSquaredTo(turn.enemies[j].location);
                if (distance < bestDistance) {
                    best = turn.enemies[j];
                    bestDistance = distance;
                }
            }
        }
        return bestDistance <= GameConstants.VISION_RADIUS_SQUARED ? best : null;
    }

    private MapLocation nearestSpawn(MapLocation from) {
        return nearestLocation(spawnLocations, from);
    }

    private static MapLocation nearestLocation(MapLocation[] locations, MapLocation from) {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < locations.length; i++) {
            MapLocation candidate = locations[i];
            int distance = from.distanceSquaredTo(candidate);
            if (distance < bestDistance
                    || (distance == bestDistance && compareLocation(candidate, best) < 0)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static FlagInfo nearestGroundFlag(FlagInfo[] flags, MapLocation from) {
        FlagInfo best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < flags.length; i++) {
            FlagInfo candidate = flags[i];
            if (candidate.isPickedUp()) continue;
            int distance = from.distanceSquaredTo(candidate.getLocation());
            if (distance < bestDistance
                    || (distance == bestDistance
                    && compareLocation(candidate.getLocation(), best == null ? null : best.getLocation()) < 0)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static RobotInfo nearestCarrier(RobotInfo[] robots, MapLocation from) {
        RobotInfo best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < robots.length; i++) {
            if (!robots[i].hasFlag) continue;
            int distance = from.distanceSquaredTo(robots[i].location);
            if (distance < bestDistance || (distance == bestDistance && robots[i].ID < best.ID)) {
                best = robots[i];
                bestDistance = distance;
            }
        }
        return best;
    }

    private static RobotInfo nearestRobot(RobotInfo[] robots, MapLocation from) {
        RobotInfo best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < robots.length; i++) {
            int distance = from.distanceSquaredTo(robots[i].location);
            if (distance < bestDistance || (distance == bestDistance && robots[i].ID < best.ID)) {
                best = robots[i];
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int compareLocation(MapLocation a, MapLocation b) {
        if (b == null) return -1;
        if (a.x != b.x) return a.x - b.x;
        return a.y - b.y;
    }

    private void setIndicator() {
        controller.setIndicatorString(PolicySupport.indicator(objective, navigator.getModeCode()));
    }

    public TurnContext getContext() {
        return context;
    }
}
