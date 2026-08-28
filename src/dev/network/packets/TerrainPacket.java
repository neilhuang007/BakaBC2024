package dev.network.packets;

import battlecode.common.GameActionException;
import dev.network.PacketBuffer;
import dev.world.Block;

public class TerrainPacket implements Packet {
    public static final int PACKET_ID = 1;

    /**
     * x, y ∈ 0..59, 6 bits.
     */
    private int x;
    private int y;
    private Block block;

    public TerrainPacket() {
    }

    public TerrainPacket(int x, int y, Block block) {
        this.x = x;
        this.y = y;
        this.block = block;
    }

    @Override
    public int getPacketID() {
        return PACKET_ID;
    }

    @Override
    public void write(PacketBuffer buf) throws GameActionException {
        buf.writeBits(x, 6);
        buf.writeBits(y, 6);
        buf.writeBits(block.ordinal(), 2);
    }

    @Override
    public void read(PacketBuffer buf) throws GameActionException {
        x = (int) buf.readBits(6);
        y = (int) buf.readBits(6);
        block = Block.VALUES[(int) buf.readBits(2)];
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Block getBlock() {
        return block;
    }
}
