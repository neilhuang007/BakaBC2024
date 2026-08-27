package baseline.strategy;

import battlecode.common.MapLocation;

public final class Objective {
    public final ObjectiveType type;
    public final MapLocation target;
    public final boolean cautious;

    public Objective(ObjectiveType type, MapLocation target, boolean cautious) {
        this.type = type;
        this.target = target;
        this.cautious = cautious;
    }
}
