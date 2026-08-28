package dev.network.packets;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import dev.network.PacketBuffer;

public class OpponentFlagPacket implements Packet {
    public static final int PACKET_ID = 4;

    private MapLocation pos;

    public OpponentFlagPacket() {
    }

    public OpponentFlagPacket(MapLocation pos) {
        this.pos = pos;
    }

    @Override
    public int getPacketID() {
        return PACKET_ID;
    }

    @Override
    public void write(PacketBuffer buf) throws GameActionException {
        buf.writeBits(pos.x, 6);
        buf.writeBits(pos.y, 6);
    }

    @Override
    public void read(PacketBuffer buf) throws GameActionException {
        pos = new MapLocation(
                (int) buf.readBits(6),
                (int) buf.readBits(6)
        );
    }

    public MapLocation getPos() {
        return pos;
    }
}
