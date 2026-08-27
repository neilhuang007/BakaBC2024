package baseline.network.packets;

import battlecode.common.GameActionException;
import baseline.network.PacketBuffer;
import baseline.world.Block;

public class TerrainMapPacket implements Packet {
    public static final int PACKET_ID = 2;

    /**
     * beginX, beginY ∈ 0..54, 6 bits.
     */
    private int beginX;
    private int beginY;
    private final Block[][] map;  // 6x6

    public TerrainMapPacket() {
        this.map = new Block[6][6];
    }

    public TerrainMapPacket(int beginX, int beginY, Block[][] map) {
        this.beginX = beginX;
        this.beginY = beginY;
        this.map = map;
    }

    @Override
    public int getPacketID() {
        return PACKET_ID;
    }

    @Override
    public void write(PacketBuffer buf) throws GameActionException {
        buf.writeBits(beginX, 6);
        buf.writeBits(beginY, 6);

        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                Block block = map[x][y];
                assert block != Block.UNKNOWN;

                buf.writeBits(block.ordinal(), 2);
            }
        }
    }

    @Override
    public void read(PacketBuffer buf) throws GameActionException {
        beginX = (int) buf.readBits(6);
        beginY = (int) buf.readBits(6);

        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                map[x][y] = Block.VALUES[(int) buf.readBits(2)];
            }
        }
    }

    public int getBeginX() {
        return beginX;
    }

    public int getBeginY() {
        return beginY;
    }

    public Block[][] getMap() {
        return map;
    }
}
