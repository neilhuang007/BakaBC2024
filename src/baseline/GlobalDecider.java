package baseline;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import baseline.entities.LocalBot;
import baseline.network.packets.IDAcquirePacket;
import baseline.world.World;

/**
 * 全局决策器
 */
public class GlobalDecider {
    private final RobotController controller;
    private final World world;
    private GameState state = GameState.BEGIN;
    private final LocalBot bot;
    private int lastLegacyBotID = -1;

    public GlobalDecider(RobotController controller) {
        this.controller = controller;
        this.world = new World(controller);
        this.bot = new LocalBot(this, controller.getID());
    }

    public void onTick() throws GameActionException {
        state = stateForRound(controller.getRoundNum());
        bot.onTick();
    }

    static GameState stateForRound(int round) {
        return round <= GameConstants.SETUP_ROUNDS ? GameState.PREPARE : GameState.NORMAL;
    }

    /**
     * 为了保持后续 network 源码可编译而保留的兼容入口。
     * M1 运行链路不会调用它。
     */
    public void onIDAcquirePacket(IDAcquirePacket packet) {
        if (packet.getId() > lastLegacyBotID) {
            lastLegacyBotID = packet.getId();
        }
    }

    public RobotController getController() {
        return controller;
    }

    public World getWorld() {
        return world;
    }

    public GameState getState() {
        return state;
    }

    public LocalBot getBot() {
        return bot;
    }

}
