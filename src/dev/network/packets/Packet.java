package dev.network.packets;

import battlecode.common.GameActionException;
import dev.network.PacketBuffer;

public interface Packet {
    int PACKET_ID_SIZE = 3;

    /**
     * 数据包类型唯一ID
     * @return 一个0..8的整数
     */
    int getPacketID();

    void write(PacketBuffer buf) throws GameActionException;

    void read(PacketBuffer buf) throws GameActionException;
}
