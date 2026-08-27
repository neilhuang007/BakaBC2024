package baseline.strategy;

import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;

public final class PolicySupport {
    private PolicySupport() {
    }

    public static RobotInfo selectAttackTarget(
            RobotInfo[] enemies,
            MapLocation origin,
            int attackDamage
    ) {
        RobotInfo best = null;
        for (int i = 0; i < enemies.length; i++) {
            RobotInfo candidate = enemies[i];
            if (origin.distanceSquaredTo(candidate.location) > GameConstants.ATTACK_RADIUS_SQUARED) {
                continue;
            }
            if (best == null || compareAttack(candidate, best, origin, attackDamage) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static int compareAttack(
            RobotInfo a,
            RobotInfo b,
            MapLocation origin,
            int attackDamage
    ) {
        if (a.hasFlag != b.hasFlag) return a.hasFlag ? -1 : 1;
        boolean aKill = a.health <= attackDamage;
        boolean bKill = b.health <= attackDamage;
        if (aKill != bKill) return aKill ? -1 : 1;
        if (a.health != b.health) return a.health - b.health;
        int aDistance = origin.distanceSquaredTo(a.location);
        int bDistance = origin.distanceSquaredTo(b.location);
        if (aDistance != bDistance) return aDistance - bDistance;
        return a.ID - b.ID;
    }

    public static RobotInfo selectHealTarget(RobotInfo[] allies, MapLocation origin) {
        RobotInfo best = null;
        for (int i = 0; i < allies.length; i++) {
            RobotInfo candidate = allies[i];
            if (candidate.health >= GameConstants.DEFAULT_HEALTH
                    || origin.distanceSquaredTo(candidate.location) > GameConstants.HEAL_RADIUS_SQUARED) {
                continue;
            }
            if (best == null || compareHeal(candidate, best, origin) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static int compareHeal(RobotInfo a, RobotInfo b, MapLocation origin) {
        if (a.hasFlag != b.hasFlag) return a.hasFlag ? -1 : 1;
        if (a.health != b.health) return a.health - b.health;
        int aDistance = origin.distanceSquaredTo(a.location);
        int bDistance = origin.distanceSquaredTo(b.location);
        if (aDistance != bDistance) return aDistance - bDistance;
        return a.ID - b.ID;
    }

    public static MapLocation explorationTarget(
            int width,
            int height,
            int stableId,
            int epoch
    ) {
        int index = Math.floorMod(stableId, 16);
        index = Math.floorMod(index + epoch * 5, 16);
        int column = index & 3;
        int row = index >>> 2;
        int x = Math.max(0, Math.min(width - 1, ((column + 1) * width) / 5));
        int y = Math.max(0, Math.min(height - 1, ((row + 1) * height) / 5));
        return new MapLocation(x, y);
    }

    public static int[] cyclicIndexes(int length, int stableId) {
        int[] result = new int[length];
        int start = Math.floorMod(stableId, length);
        for (int i = 0; i < length; i++) {
            result[i] = (start + i) % length;
        }
        return result;
    }

    public static String indicator(Objective objective, String navigationMode) {
        MapLocation target = objective == null ? null : objective.target;
        String coordinate = target == null ? "-" : target.x + "," + target.y;
        String type = objective == null ? "NONE" : objective.type.code;
        String value = "LOCAL|" + type + "|" + coordinate + "|" + navigationMode;
        if (value.length() > GameConstants.INDICATOR_STRING_MAX_LENGTH) {
            return value.substring(0, GameConstants.INDICATOR_STRING_MAX_LENGTH);
        }
        return value;
    }
}
