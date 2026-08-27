package baseline.entities;

import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;

public class Bot {
    private final int bcID;
    private final Team team;
    public int x = -1;
    public int y = -1;
    public int health;
    public boolean hasFlag;
    public int attackLevel;
    public int healLevel;
    public int buildLevel;

    public Bot(int bcID, Team team) {
        this.bcID = bcID;
        this.team = team;
    }

    public Bot(RobotInfo info) {
        this(info.ID, info.team);
        updateFromInfo(info);
    }

    public void updateFromInfo(RobotInfo info) {
        assert bcID == info.ID;

        MapLocation location = info.location;
        this.x = location.x;
        this.y = location.y;
        this.health = info.health;
        this.hasFlag = info.hasFlag;
        this.attackLevel = info.attackLevel;
        this.healLevel = info.healLevel;
        this.buildLevel = info.buildLevel;
    }

    public int getBcID() {
        return bcID;
    }

    public Team getTeam() {
        return team;
    }

    public boolean isAlive() {
        return health > 0;
    }
}
