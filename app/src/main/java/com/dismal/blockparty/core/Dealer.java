package com.dismal.blockparty.core;

public final class Dealer {

    /** Codes at or above this force a shape: {@code code - 1000} is the shape id. */
    public static final int FORCED_SHAPE_BASE = 1000;

    private static final long MASK = 0xFFFFFFFFFFFFL;
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long INCREMENT = 0xBL;

    private long state;

    /**
     * srand48(): {@code X = (seed << 16) | 0x330E}. Seed 0 keeps glibc's unseeded
     */
    public void seed(long value) {
        if (value == 0L) {
            state = 0L;
            return;
        }
        state = (((value & 0xFFFFFFFFL) << 16) | 0x330EL) & MASK;
    }

    /** One LCG step, returning the top 31 bits. */
    public int next() {
        state = (MULTIPLIER * state + INCREMENT) & MASK;
        return (int) (state >>> 17);
    }

    /**
     * Hinh::initShape(bool avoid, int code):
     * {@code id = lrand48() % 18} when avoiding the giant, otherwise {@code % 19};
     * a code of 1000+ then overwrites the drawn id.
     *
     * <p>Note the draw always happens, even for a forced shape.
     */
    public int nextShapeId(boolean avoidGiant) {
        int drawn = avoidGiant
                ? next() % PieceCatalog.GIANT_SHAPE_ID
                : next() % PieceCatalog.count();
        return drawn;
    }

    /** Draw with a forced-shape code */
    public int nextShapeId(int code) {
        int drawn = next() % PieceCatalog.count();
        if (code >= FORCED_SHAPE_BASE) {
            int forced = code - FORCED_SHAPE_BASE;
            if (PieceCatalog.isValidId(forced)) {
                return forced;
            }
        }
        return drawn;
    }
}
