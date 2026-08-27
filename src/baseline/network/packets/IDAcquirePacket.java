package baseline.network.packets;

import battlecode.common.GameActionException;
import baseline.network.PacketBuffer;

public class IDAcquirePacket implements Packet {
    public static final int PACKET_ID = 3;

    /**
     * id ∈ 0..49, 6 bits
     */
    private int id;

    public IDAcquirePacket() {
    }

    public IDAcquirePacket(int id) {
        this.id = id;
    }

    @Override
    public int getPacketID() {
        return PACKET_ID;
    }

    @Override
    public void write(PacketBuffer buf) throws GameActionException {
        buf.writeBits(id, 6);
    }

    @Override
    public void read(PacketBuffer buf) throws GameActionException {
        id = (int) buf.readBits(6);
    }

    public int getId() {
        return id;
    }
}
