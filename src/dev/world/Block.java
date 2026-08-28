package dev.world;

public enum Block {
    LAND,
    WALL,
    WATER,
    DAM,
    UNKNOWN;  // 不应被放到TerrainPacket/TerrainMapPacket网络包中

    public static final Block[] VALUES = values();
}
