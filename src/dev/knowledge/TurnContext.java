package dev.knowledge;

import battlecode.common.FlagInfo;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;

/** 只表示当前回合已感知事实的不可变快照。 */
public final class TurnContext {
    public final int round;
    public final MapLocation location;
    public final boolean hasFlag;
    public final boolean actionReady;
    public final boolean movementReady;
    public final RobotInfo[] teammates;
    public final RobotInfo[] enemies;
    public final FlagInfo[] allyFlags;
    public final FlagInfo[] enemyFlags;
    public final MapLocation[] crumbs;
    public final MapInfo[] mapInfos;
    public final MapLocation[] broadcastEnemyFlags;

    private TurnContext(
            int round,
            MapLocation location,
            boolean hasFlag,
            boolean actionReady,
            boolean movementReady,
            RobotInfo[] teammates,
            RobotInfo[] enemies,
            FlagInfo[] allyFlags,
            FlagInfo[] enemyFlags,
            MapLocation[] crumbs,
            MapInfo[] mapInfos,
            MapLocation[] broadcastEnemyFlags
    ) {
        this.round = round;
        this.location = location;
        this.hasFlag = hasFlag;
        this.actionReady = actionReady;
        this.movementReady = movementReady;
        this.teammates = teammates;
        this.enemies = enemies;
        this.allyFlags = allyFlags;
        this.enemyFlags = enemyFlags;
        this.crumbs = crumbs;
        this.mapInfos = mapInfos;
        this.broadcastEnemyFlags = broadcastEnemyFlags;
    }

    public static TurnContext capture(RobotController controller) throws GameActionException {
        Team team = controller.getTeam();
        return new TurnContext(
                controller.getRoundNum(),
                controller.getLocation(),
                controller.hasFlag(),
                controller.isActionReady(),
                controller.isMovementReady(),
                controller.senseNearbyRobots(-1, team),
                controller.senseNearbyRobots(-1, team.opponent()),
                controller.senseNearbyFlags(-1, team),
                controller.senseNearbyFlags(-1, team.opponent()),
                controller.senseNearbyCrumbs(-1),
                controller.senseNearbyMapInfos(),
                controller.senseBroadcastFlagLocations()
        );
    }

    public MapInfo mapInfoAt(MapLocation location) {
        for (int i = 0; i < mapInfos.length; i++) {
            if (mapInfos[i].getMapLocation().equals(location)) {
                return mapInfos[i];
            }
        }
        return null;
    }
}
