package com.dismal.blockparty.core;


public final class Scoring {

    public static final int CELLS_PER_LINE = 10;

    private static final int[][] NONE = {};
    private static final int[][] DOUBLE_AT_3 = {{3, 2}};
    private static final int[][] UP_TO_3 = {{3, 2}, {4, 3}};
    private static final int[][] UP_TO_5 = {{3, 2}, {4, 3}, {5, 4}, {6, 5}};
    private static final int[][] O_PIECE = {{2, 2}, {3, 3}};

    private Scoring() {
    }

    /**
     * Combo multiplier for a placement. Shape families follow the original switch:
     * the single cell never combos; 2-bars double at 3 lines; 3-/4-bars and triominoes
     * reach x3; five-cell pieces and the giant reach x5; the O piece doubles at 2.
     */
    public static int comboMultiplier(int shapeId, int linesCleared) {
        if (linesCleared <= 0) {
            return 1;
        }
        int[][] steps = stepsFor(shapeId);
        int multiplier = 1;
        for (int[] step : steps) {
            if (linesCleared >= step[0]) {
                multiplier = step[1];
            }
        }
        return multiplier;
    }

    private static int[][] stepsFor(int shapeId) {
        switch (shapeId) {
            case 0:
                return NONE;
            case 1:
            case 5:
                return DOUBLE_AT_3;
            case 2:
            case 3:
            case 6:
            case 7:
            case 9:
            case 10:
            case 11:
            case 12:
                return UP_TO_3;
            case 4:
            case 8:
            case 13:
            case 14:
            case 15:
            case 16:
            case 18:
                return UP_TO_5;
            case 17:
                return O_PIECE;
            default:
                return NONE;
        }
    }

    /** pieceCells + 10 * linesCleared, before any multiplier. */
    public static int placementScore(int pieceCells, int linesCleared) {
        return pieceCells + CELLS_PER_LINE * linesCleared;
    }

    /** Full award for one placement. */
    public static int scorePlacement(int shapeId, int pieceCells, int linesCleared) {
        int base = placementScore(pieceCells, linesCleared);
        if (linesCleared <= 0) {
            return base;
        }
        return base * comboMultiplier(shapeId, linesCleared);
    }
}
