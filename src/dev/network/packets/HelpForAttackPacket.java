package dev.network.packets;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import dev.network.PacketBuffer;

/**
 * 呼叫其他人来打团
 */
public class HelpForAttackPacket implements Packet {
    public static final int PACKET_ID = 0;

    public MapLocation pos;
    // TODO 随着curRound - round变大，认领任务的maxCost也增大；即如果一直没有认领，那么认领范围会变广，直到全部被认领。
    public int round;
    public boolean highPriory;
    public int requires;  // requires ∈ 1..16, 4 bits

    public HelpForAttackPacket() {
    }

    public HelpForAttackPacket(MapLocation pos, int round, boolean highPriory, int requires) {
        this.pos = pos;
        this.round = round;
        this.highPriory = highPriory;
        this.requires = requires;
    }

    @Override
    public int getPacketID() {
        return PACKET_ID;
    }

    @Override
    public void write(PacketBuffer buf) throws GameActionException {
        buf.writeBits(pos.x, 6);
        buf.writeBits(pos.y, 6);
        buf.writeBoolean(highPriory);
        buf.writeBits(requires - 1, 4);  // 归一化到0..15
    }

    @Override
    public void read(PacketBuffer buf) throws GameActionException {
        pos = new MapLocation(
                (int) buf.readBits(6),
                (int) buf.readBits(6)
        );
        highPriory = buf.readBoolean();
        requires = (int) buf.readBits(4) + 1;
    }
}
