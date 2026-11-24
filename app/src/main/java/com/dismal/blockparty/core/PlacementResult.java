package com.dismal.blockparty.core;

/** Outcome of one placement attempt. */
public final class PlacementResult {

    public boolean placed;
    public int shapeId;
    public int linesCleared;
    /** pieceCells + 10 * linesCleared */
    public int baseScore;
    public int multiplier = 1;
    /** What the score actually gained. */
    public int gained;
    public int[] rows = new int[0];
    public int[] columns = new int[0];

    public boolean isCombo() {
        return placed && multiplier > 1;
    }
}
