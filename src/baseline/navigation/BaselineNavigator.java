package baseline.navigation;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import baseline.knowledge.TurnContext;

/** 确定性贪心导航，在局部极小值处退化为贴墙。 */
public final class BaselineNavigator {
    private static final Direction[] DIRECTIONS = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };
    private static final int HISTORY_SIZE = 10;

    private final RobotController controller;
    private final boolean initialFollowLeft;
    private final MapLocation[] recent = new MapLocation[HISTORY_SIZE];
    private int recentIndex;
    private int recentCount;

    private MapLocation target;
    private boolean wallFollowing;
    private boolean followLeft;
    private Direction wallDirection = Direction.CENTER;
    private int wallHitDistance;
    private int wallSteps;
    private String modeCode = "IDLE";

    public BaselineNavigator(RobotController controller, int stableId) {
        this.controller = controller;
        this.initialFollowLeft = (stableId & 1) == 0;
        this.followLeft = initialFollowLeft;
    }

    public Direction nextStep(MapLocation newTarget, TurnContext context, boolean cautious) {
        if (newTarget == null || context.location.equals(newTarget) || !context.movementReady) {
            modeCode = "IDLE";
            remember(context.location);
            return Direction.CENTER;
        }
        if (!newTarget.equals(target)) {
            resetForTarget(newTarget);
        }

        MapLocation here = context.location;
        Direction direct = here.directionTo(newTarget);
        int currentDistance = here.distanceSquaredTo(newTarget);

        if (wallFollowing) {
            if (controller.canMove(direct) && currentDistance < wallHitDistance) {
                wallFollowing = false;
                wallSteps = 0;
            } else {
                Direction wallStep = wallStep();
                if (wallStep != Direction.CENTER) {
                    wallSteps++;
                    if (wallSteps > 24 || isRecent(here.add(wallStep), 2)) {
                        followLeft = !followLeft;
                        wallSteps = 0;
                    }
                    modeCode = followLeft ? "WALL-L" : "WALL-R";
                    remember(here);
                    return wallStep;
                }
            }
        }

        Direction best = bestGreedyStep(newTarget, context, cautious, direct);
        if (best != Direction.CENTER) {
            MapLocation next = here.add(best);
            if (next.distanceSquaredTo(newTarget) < currentDistance || best == direct) {
                modeCode = cautious ? "SAFE" : "DIRECT";
                remember(here);
                return best;
            }
        }

        wallFollowing = true;
        wallDirection = direct;
        wallHitDistance = currentDistance;
        wallSteps = 0;
        Direction result = wallStep();
        modeCode = followLeft ? "WALL-L" : "WALL-R";
        remember(here);
        return result;
    }

    private Direction bestGreedyStep(
            MapLocation target,
            TurnContext context,
            boolean cautious,
            Direction direct
    ) {
        Direction best = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < DIRECTIONS.length; i++) {
            Direction direction = DIRECTIONS[i];
            if (!controller.canMove(direction)) continue;
            MapLocation next = context.location.add(direction);
            int score = -100 * next.distanceSquaredTo(target);
            score -= rotationDistance(direct, direction) * 4;
            if (isRecent(next, HISTORY_SIZE)) score -= 350;
            if (cautious) score -= dangerScore(next, context.enemies);
            if (score > bestScore
                    || (score == bestScore && direction.getDirectionOrderNum() < best.getDirectionOrderNum())) {
                best = direction;
                bestScore = score;
            }
        }
        return best;
    }

    private static int dangerScore(MapLocation next, RobotInfo[] enemies) {
        int danger = 0;
        for (int i = 0; i < enemies.length; i++) {
            int distance = next.distanceSquaredTo(enemies[i].location);
            if (distance <= 4) danger += 900;
            else if (distance <= 10) danger += 90;
        }
        return danger;
    }

    private Direction wallStep() {
        Direction candidate = wallDirection;
        for (int i = 0; i < 8; i++) {
            candidate = followLeft ? candidate.rotateRight() : candidate.rotateLeft();
            if (controller.canMove(candidate)) {
                wallDirection = followLeft ? candidate.rotateLeft() : candidate.rotateRight();
                return candidate;
            }
        }
        return Direction.CENTER;
    }

    private static int rotationDistance(Direction a, Direction b) {
        if (a == Direction.CENTER || b == Direction.CENTER) return 8;
        int delta = Math.abs(a.getDirectionOrderNum() - b.getDirectionOrderNum());
        return Math.min(delta, 8 - delta);
    }

    private void resetForTarget(MapLocation newTarget) {
        target = newTarget;
        wallFollowing = false;
        followLeft = initialFollowLeft;
        wallDirection = Direction.CENTER;
        wallSteps = 0;
    }

    public void reset() {
        target = null;
        wallFollowing = false;
        wallSteps = 0;
        recentCount = 0;
        recentIndex = 0;
        modeCode = "IDLE";
    }

    private void remember(MapLocation location) {
        recent[recentIndex] = location;
        recentIndex = (recentIndex + 1) % HISTORY_SIZE;
        if (recentCount < HISTORY_SIZE) recentCount++;
    }

    private boolean isRecent(MapLocation location, int limit) {
        int count = Math.min(recentCount, limit);
        for (int i = 1; i <= count; i++) {
            int index = Math.floorMod(recentIndex - i, HISTORY_SIZE);
            if (location.equals(recent[index])) return true;
        }
        return false;
    }

    public String getModeCode() {
        return modeCode;
    }
}
