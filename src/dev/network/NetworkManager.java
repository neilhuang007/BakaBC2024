package dev.network;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import dev.GlobalDecider;
import dev.network.packets.*;
import dev.world.Block;

import java.util.Arrays;

public class NetworkManager {
    private final GlobalDecider decider;
    private final PacketBuffer buffer;

    public NetworkManager(GlobalDecider decider, RobotController controller) {
        this.decider = decider;
        this.buffer = new PacketBuffer(controller);
    }

    public void sendHelpForAttackPacket(MapLocation pos, boolean highPriory, int requires) throws GameActionException {
        sendPacket0(new HelpForAttackPacket(
                pos, decider.getController().getRoundNum(), highPriory, requires
        ));
    }

    public void sendTerrainPacket(MapLocation pos, Block block) throws GameActionException {
        int x = pos.x;
        int y = pos.y;

        assert x >= 0 && y >= 0 && x <= 59 && y <= 59;
        assert block != Block.UNKNOWN;
        sendPacket0(new TerrainPacket(x, y, block));
    }

    public void sendTerrainMapPacket(MapLocation begin, Block[][] map) throws GameActionException {
        int beginX = begin.x;
        int beginY = begin.y;

        assert beginX >= 0 && beginY >= 0 && beginX <= 54 && beginY <= 54;
        assert map.length == 6;
        assert Arrays.stream(map).allMatch(line -> line.length == 6);
        assert Arrays.stream(map).flatMap(Arrays::stream).allMatch(block -> block != Block.UNKNOWN);
        sendPacket0(new TerrainMapPacket(beginX, beginY, map));
    }

    public void sendIDAcquirePacket(int id) throws GameActionException {
        assert id >= 0 && id <= 49;
        sendPacket0(new IDAcquirePacket(id));
    }

    private void sendPacket0(Packet packet) throws GameActionException {
        buffer.writeBits(packet.getPacketID(), Packet.PACKET_ID_SIZE);
        packet.write(buffer);
    }

    private void onReceiveHelpForAttackPacket() throws GameActionException {
        HelpForAttackPacket packet = new HelpForAttackPacket();
        packet.read(buffer);

        // TODO 更新到TurnContext并实际影响Goal
        // 认领任务后，我们再发一个相同坐标的、requires减少的包，防止数据包被ring buffer刷掉。
        // 以坐标为准，判断是否是同一个任务。对于相同坐标的任务，只能认领一次。
        // 类似的，发起者或其他在场者也可以通过发布一个相同坐标，但requires = 0的HelpForAttackPacket取消任务，如果他们判断不再需要的话。
    }

    private void onReceiveTerrainPacket() throws GameActionException {
        TerrainPacket packet = new TerrainPacket();
        packet.read(buffer);

        decider.getWorld().setBlock(packet.getX(), packet.getY(), packet.getBlock());
        // TODO 更新到Knowledge，以区分方块更新是否需要广播
    }

    private void onReceiveTerrainMapPacket() throws GameActionException {
        TerrainMapPacket packet = new TerrainMapPacket();
        packet.read(buffer);

        int beginX = packet.getBeginX();
        int beginY = packet.getBeginY();
        Block[][] map = packet.getMap();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                decider.getWorld().setBlock(beginX + i, beginY + j, map[i][j]);
            }
        }
        // TODO 更新到Knowledge，以区分方块更新是否需要广播
    }

    private void onReceiveIDAcquirePacket() throws GameActionException {
        IDAcquirePacket packet = new IDAcquirePacket();
        packet.read(buffer);

        decider.onIDAcquirePacket(packet);
    }

    private void onReceiveOpponentFlagPacket() throws GameActionException {
        OpponentFlagPacket packet = new OpponentFlagPacket();
        packet.read(buffer);
        // M1 的 TurnContext 是逐回合不可变快照，network 也不进入运行链路。
        // 后续在 M2 由 LocalKnowledge/blackboard 消费该 packet。
    }

    public void onPreTick() throws GameActionException {
        buffer.onPreTick();
        while (buffer.hasReadableBits(Packet.PACKET_ID_SIZE)) {
            int id = (int) buffer.readBits(Packet.PACKET_ID_SIZE);

            switch (id) {
                case HelpForAttackPacket.PACKET_ID:
                    onReceiveHelpForAttackPacket();
                    break;
                case TerrainPacket.PACKET_ID:
                    onReceiveTerrainPacket();
                    break;
                case TerrainMapPacket.PACKET_ID:
                    onReceiveTerrainMapPacket();
                    break;
                case IDAcquirePacket.PACKET_ID:
                    onReceiveIDAcquirePacket();
                    break;
                case OpponentFlagPacket.PACKET_ID:
                    onReceiveOpponentFlagPacket();
                    break;
                default:
                    assert false;
            }
        }
    }

    public void onPostTick() throws GameActionException {
        buffer.onPostTick();
    }
}
