package dev.opening;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;

/** One budget gate for every strategic crumb expense. */
public final class EconomyBudget {
    public static final int SETUP_RESERVE = 200;
    public static final int NORMAL_RESERVE = 1000;
    public static final int HARD_FLOOR = 200;

    private final RobotController controller;
    private final OpeningSharedState shared;
    private int lastRound = -1;
    private int lastCrumbs;
    private int lastStrategicSpend;
    private int rollingGrossIncome = GameConstants.PASSIVE_CRUMBS_INCREASE;
    private int forecast;

    public EconomyBudget(RobotController controller, OpeningSharedState shared) {
        this.controller = controller;
        this.shared = shared;
    }

    public void update(boolean coordinator) throws GameActionException {
        int round = controller.getRoundNum();
        int crumbs = controller.getCrumbs();
        shared.recordMinimumCrumbs(crumbs);
        if (coordinator) {
            int spend = shared.readStrategicSpend();
            if (lastRound >= 0 && round > lastRound) {
                int elapsed = round - lastRound;
                int gross = crumbs - lastCrumbs + Math.max(0, spend - lastStrategicSpend);
                int perRound = gross / elapsed;
                rollingGrossIncome = (rollingGrossIncome * 19 + Math.max(0, perRound)) / 20;
            }
            int horizon = round <= GameConstants.SETUP_ROUNDS
                    ? GameConstants.SETUP_ROUNDS - round
                    : 50;
            forecast = Math.min(0xFFFF, crumbs + rollingGrossIncome * Math.max(0, horizon));
            shared.writeEconomyWord(forecast);
            lastRound = round;
            lastCrumbs = crumbs;
            lastStrategicSpend = spend;
        } else {
            forecast = shared.readEconomyWord();
            if (forecast == 0) forecast = crumbs;
        }
    }

    public boolean canSpendStrategic(int cost) {
        int reserve = reserveForRound(controller.getRoundNum());
        return controller.getCrumbs() - cost >= reserve && forecast - cost >= reserve;
    }

    public boolean canSpendTactical(int cost) {
        return controller.getCrumbs() - cost >= HARD_FLOOR;
    }

    public void recordStrategicSpend(int cost) throws GameActionException {
        shared.addStrategicSpend(cost);
        shared.recordMinimumCrumbs(controller.getCrumbs());
    }

    public int forecast() {
        return forecast;
    }

    public static int reserveForRound(int round) {
        return round <= GameConstants.SETUP_ROUNDS ? SETUP_RESERVE : NORMAL_RESERVE;
    }

    public static int damQuota(int projectedEndSetupCrumbs) {
        return Math.min(10, Math.max(0, (projectedEndSetupCrumbs - SETUP_RESERVE) / 100));
    }
}
