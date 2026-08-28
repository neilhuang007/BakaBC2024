package dev.objects;

import dev.entities.Bot;

public final class BotMap {
    private static final int CAPACITY = 128;
    private static final int MASK = CAPACITY - 1;

    private final int[] ids = new int[CAPACITY];
    private final Bot[] values = new Bot[CAPACITY];

    public void put(int id, Bot bot) {
        int index = hash(id);

        while (true) {
            int existing = ids[index];

            if (existing == 0 || existing == id) {
                ids[index] = id;
                values[index] = bot;
                return;
            }

            index = (index + 1) & MASK;
        }
    }

    public Bot get(int id) {
        int index = hash(id);

        while (true) {
            int existing = ids[index];

            if (existing == id) {
                return values[index];
            }

            if (existing == 0) {
                return null;
            }

            index = (index + 1) & MASK;
        }
    }

    private static int hash(int id) {
        int h = id * 0x9E3779B9;
        return h >>> 25; // 32 - log2(128)
    }
}