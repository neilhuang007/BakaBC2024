package baseline.network;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class PacketBuffer {

    private static final int WORD_COUNT = 64;
    private static final int WORD_BITS = 16;

    /**
     * 整个 shared array 的 bit 数。
     * <p>
     * bit 0..9:
     *     ring write pointer
     * <p>
     * bit 10..1023:
     *     ring data
     */
    private static final int BUFFER_SIZE = WORD_COUNT * WORD_BITS; // 1024

    private static final int POINTER_BITS = 10;
    private static final int POINTER_MASK = (1 << POINTER_BITS) - 1; // 0x3FF

    private static final int DATA_START = POINTER_BITS; // bit 10
    private static final int DATA_CAPACITY = BUFFER_SIZE - DATA_START;

    private static final int WORD_MASK = 0xFFFF;

    private final RobotController controller;

    /**
     * 当前 bot 的消费位置。
     */
    private int readPtr = DATA_START;

    /**
     * 当前 turn 中的本地写指针。
     * 每个 turn 第一次写入时，从 shared[0] 读取真实 global pointer。
     */
    private int writePtr = -1;

    /**
     * 当前 turn 的 64 个 shared words 的本地 cache。
     * 每个元素仍然使用 Java int 保存，
     * 但值域始终保持 0..65535。
     */
    private final int[] cache = new int[WORD_COUNT];

    /**
     * bit i == 1 => cache[i] 当前有效。
     */
    private long validMask;

    /**
     * bit i == 1 => cache[i] 被本 bot 修改，需要 flush。
     */
    private long dirtyMask;

    public PacketBuffer(RobotController controller) {
        this.controller = controller;
    }

    public void onPreTick() {
        validMask = 0;
        dirtyMask = 0;
        writePtr = -1;
    }

    public void onPostTick() throws GameActionException {
        if (dirtyMask == 0) {
            return;
        }

        for (int i = 0; i < WORD_COUNT; i++) {
            long bit = 1L << i;

            if ((dirtyMask & bit) != 0) {
                controller.writeSharedArray(i, cache[i]);
            }
        }

        dirtyMask = 0;
    }

    // ------------------------------------------------------------------------
    // Read API
    // ------------------------------------------------------------------------

    public boolean readBoolean() throws GameActionException {
        return readBits(1) != 0;
    }

    public byte readByte() throws GameActionException {
        return (byte) readBits(8);
    }

    public short readShort() throws GameActionException {
        return (short) readBits(16);
    }

    public int readInt() throws GameActionException {
        return (int) readBits(32);
    }

    public long readLong() throws GameActionException {
        return readBits(64);
    }

    // ------------------------------------------------------------------------
    // Write API
    // ------------------------------------------------------------------------

    public boolean writeBoolean(boolean value) throws GameActionException {
        writeBits(value ? 1L : 0L, 1);
        return value;
    }

    public byte writeByte(byte value) throws GameActionException {
        writeBits(value, 8);
        return value;
    }

    public short writeShort(short value) throws GameActionException {
        writeBits(value, 16);
        return value;
    }

    public int writeInt(int value) throws GameActionException {
        writeBits(value, 32);
        return value;
    }

    public long writeLong(long value) throws GameActionException {
        writeBits(value, 64);
        return value;
    }

    // ------------------------------------------------------------------------
    // Arbitrary bit fields
    // ------------------------------------------------------------------------

    /**
     * 读取 1..64 bit。
     * bitstream 使用 little-endian / LSB-first。
     */
    public long readBits(int bitCount) throws GameActionException {
        checkBitCount(bitCount);

        long result = 0;
        int resultShift = 0;
        int remaining = bitCount;

        while (remaining > 0) {
            int wordIndex = readPtr >>> 4;
            int bitOffset = readPtr & 15;

            int bitsUntilWordEnd = WORD_BITS - bitOffset;
            int bitsUntilBufferEnd = BUFFER_SIZE - readPtr;

            int chunk = Math.min(
                    remaining,
                    Math.min(bitsUntilWordEnd, bitsUntilBufferEnd)
            );

            int word = readWord(wordIndex);

            int mask = mask(chunk);

            int part = (word >>> bitOffset) & mask;

            result |= ((long) part) << resultShift;

            updateReadPtr(chunk);

            remaining -= chunk;
            resultShift += chunk;
        }

        return result;
    }

    /**
     * 写入 value 的低 bitCount 位。
     * 支持 1..64 bit。
     * 例如：
     * writeBits(0b1101, 4)
     * 在 bitstream 中依次写入：
     * 1, 0, 1, 1
     */
    public void writeBits(long value, int bitCount)
            throws GameActionException {

        checkBitCount(bitCount);
        ensureWritePtr();

        int valueShift = 0;
        int remaining = bitCount;

        while (remaining > 0) {
            int wordIndex = writePtr >>> 4;
            int bitOffset = writePtr & 15;

            int bitsUntilWordEnd = WORD_BITS - bitOffset;
            int bitsUntilBufferEnd = BUFFER_SIZE - writePtr;

            int chunk = Math.min(
                    remaining,
                    Math.min(bitsUntilWordEnd, bitsUntilBufferEnd)
            );

            int chunkMask = mask(chunk);

            int part = (int) ((value >>> valueShift) & chunkMask);

            /*
             * 如果我们正好覆盖完整的一个 16-bit word，
             * 就不必先读取旧值。
             *
             * 省一次 readSharedArray。
             */
            int oldWord;

            if (bitOffset == 0 && chunk == WORD_BITS) {
                oldWord = 0;
            } else {
                oldWord = readWord(wordIndex);
            }

            int shiftedMask = chunkMask << bitOffset;

            int newWord =
                    (oldWord & ~shiftedMask)
                            | (part << bitOffset);

            writeWord(wordIndex, newWord);

            updateWritePtr(chunk);

            remaining -= chunk;
            valueShift += chunk;
        }
    }

    // ------------------------------------------------------------------------
    // Cache
    // ------------------------------------------------------------------------

    private int readWord(int index) throws GameActionException {
        long bit = 1L << index;

        if ((validMask & bit) != 0) {
            return cache[index];
        }

        int value = controller.readSharedArray(index);

        cache[index] = value;
        validMask |= bit;

        return value;
    }

    private void writeWord(int index, int value) {
        value &= WORD_MASK;

        cache[index] = value;

        long bit = 1L << index;

        validMask |= bit;
        dirtyMask |= bit;
    }

    // ------------------------------------------------------------------------
    // Reader utilities
    // ------------------------------------------------------------------------

    /**
     * 当前 reader 到 global write pointer 之间还有多少 bit。
     * <p>
     * 注意：
     * 由于这里只有一个 10-bit head pointer，
     * 无法区分：
     * <p>
     *   reader == writer，因为没有新数据
     * <p>
     * 和
     * <p>
     *   writer 已经整整绕了一圈追上 reader
     * <p>
     * 所以这里假设 producer 不会在一个 bot 消费之前
     * 写满整个 1014-bit ring。
     */
    public int readableBits() throws GameActionException {
        int head = currentWritePtr();

        if (head >= readPtr) {
            return head - readPtr;
        }

        return (BUFFER_SIZE - readPtr)
                + (head - DATA_START);
    }

    public boolean hasReadableBits(int bitCount)
            throws GameActionException {

        if (bitCount < 0 || bitCount > DATA_CAPACITY) {
            return false;
        }

        return readableBits() >= bitCount;
    }

    /**
     * 放弃这个 bot 尚未读取的所有消息，
     * 从当前 global head 重新开始。
     */
    public void resetReaderToHead() throws GameActionException {
        readPtr = currentWritePtr();
    }

    // ------------------------------------------------------------------------
    // Pointer management
    // ------------------------------------------------------------------------

    private void ensureWritePtr() throws GameActionException {
        if (writePtr != -1) {
            return;
        }

        writePtr = decodePointer(readWord(0));
    }

    private int currentWritePtr() throws GameActionException {
        if (writePtr != -1) {
            return writePtr;
        }

        return decodePointer(readWord(0));
    }

    /**
     * shared[0] 初始值默认是 0。
     *
     * 但是 bit 0..9 是 metadata，不能作为 payload 地址，
     * 所以 0..9 都被 normalize 成 DATA_START (= 10)。
     */
    private static int decodePointer(int word0) {
        int ptr = word0 & POINTER_MASK;

        return Math.max(ptr, DATA_START);

    }

    private void updateReadPtr(int amount) {
        readPtr = advancePointer(readPtr, amount);
    }

    private void updateWritePtr(int amount)
            throws GameActionException {

        writePtr = advancePointer(writePtr, amount);

        /*
         * 修改 shared[0] 的低 10 bit，
         * 保留 bit 10..15。
         */
        int word0 = readWord(0);

        int newWord0 =
                (word0 & ~POINTER_MASK)
                        | (writePtr & POINTER_MASK);

        writeWord(0, newWord0);
    }

    private static int advancePointer(int ptr, int amount) {
        int next = ptr + amount;

        if (next >= BUFFER_SIZE) {
            next = DATA_START + (next - BUFFER_SIZE);
        }

        return next;
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static int mask(int bits) {
        /*
         * bits 最大只有 16，因为这里用于单个 shared word。
         */
        return (1 << bits) - 1;
    }

    private static void checkBitCount(int bitCount) {
        if (bitCount < 1 || bitCount > 64) {
            throw new IllegalArgumentException(
                    "bitCount must be between 1 and 64: " + bitCount
            );
        }
    }
}