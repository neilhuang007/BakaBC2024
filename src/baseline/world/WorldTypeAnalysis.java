package baseline.world;

public class WorldTypeAnalysis {
    private final World world;
    private final int width;
    private final int height;

    private boolean mayHorizonal = true;
    private boolean mayVertical = true;
    private boolean mayRotational = true;

    public WorldTypeAnalysis(World world) {
        this.world = world;
        this.width = world.getWidth();
        this.height = world.getHeight();
    }

    /**
     * 当地图发生变化时，进行地图类型分析
     */
    public WorldType runAnalyze(int x, int y, Block block) {
        assert isTypeUnknown();

        Block block2 = world.getBlockNoFallback(x, height - y - 1);
        if (block2 != Block.UNKNOWN && block2 != block) {
            mayHorizonal = false;
        }
        block2 = world.getBlockNoFallback(width - x - 1, y);
        if (block2 != Block.UNKNOWN && block2 != block) {
            mayVertical = false;
        }
        block2 = world.getBlockNoFallback(width - x - 1, height - y - 1);
        if (block2 != Block.UNKNOWN && block2 != block) {
            mayRotational = false;
        }

        if (isTypeUnknown()) return WorldType.UNKNOWN;
        if (mayHorizonal) return WorldType.HORIZONTAL;
        if (mayVertical) return WorldType.VERTICAL;
        if (mayRotational) return WorldType.ROTATIONAL;
        assert false;
        throw new IllegalStateException("WorldTypeAnalysis");
    }

    private boolean isTypeUnknown() {
        return (mayHorizonal && mayVertical) || (mayHorizonal && mayRotational) || (mayVertical && mayRotational);
    }
}
