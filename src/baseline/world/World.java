package baseline.world;

import battlecode.common.RobotController;

public class World {
    private final Block[][] world;
    private final int width;
    private final int height;
    private final WorldTypeAnalysis analysis;
    private WorldType worldType = WorldType.ALL;
    private boolean worldTypeKnown = false;

    public World(RobotController controller) {
        this.width = controller.getMapWidth();
        this.height = controller.getMapHeight();
        this.world = new Block[width][height];
        this.analysis = new WorldTypeAnalysis(this);
    }

    public void setBlock(int x, int y, Block block) {
        assert x >= 0 && y >= 0 && x < width && y < height;

        world[x][y] = block;
        if (!worldTypeKnown) {
            WorldType analyzed = analysis.runAnalyze(x, y, block);
            if (analyzed != WorldType.UNKNOWN) {
                worldType = analyzed;
                worldTypeKnown = true;
            }
        }

        if (worldTypeKnown) {
            switch (worldType) {
                case HORIZONTAL:
                    world[x][height - y - 1] = block;
                    break;
                case VERTICAL:
                    world[width - x - 1][y] = block;
                    break;
                case ROTATIONAL:
                    world[width - x - 1][height - y - 1] = block;
                    break;
            }
        }
    }

    public Block getBlock(int x, int y) {
        Block block = getBlockNoFallback(x, y);

        if (block == Block.UNKNOWN) {
            switch (worldType) {
                case UNKNOWN:
                    return Block.UNKNOWN;
                case HORIZONTAL:
                    return getBlockNoFallback(x, height - y - 1);
                case VERTICAL:
                    return getBlockNoFallback(width - x - 1, y);
                case ROTATIONAL:
                    return getBlockNoFallback(width - x - 1, height - y - 1);
                case ALL:
                    block = getBlockNoFallback(x, height - y - 1);
                    if (block != Block.UNKNOWN) return block;
                    block = getBlockNoFallback(width - x - 1, y);
                    if (block != Block.UNKNOWN) return block;
                    block = getBlockNoFallback(width - x - 1, height - y - 1);
                    return block;
            }
        }
        return block;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    Block getBlockNoFallback(int x, int y) {
        assert x >= 0 && y >= 0 && x < width && y < height;
        Block block = world[x][y];
        return block == null ? Block.UNKNOWN : block;
    }
}
