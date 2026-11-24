package com.dismal.blockparty.core;

public final class Piece {

    public static final int GRID = 5;

    private final int shapeId;
    private final int color;
    private final boolean[] mask;
    private final int cellCount;
    private final int width;
    private final int height;

    private Piece(int shapeId, int color, boolean[] mask) {
        this.shapeId = shapeId;
        this.color = color;
        this.mask = mask;

        int cells = 0;
        int maxRow = -1;
        int maxCol = -1;
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                if (mask[row * GRID + col]) {
                    cells++;
                    if (row > maxRow) maxRow = row;
                    if (col > maxCol) maxCol = col;
                }
            }
        }
        this.cellCount = cells;
        this.width = cells == 0 ? 0 : maxCol + 1;
        this.height = cells == 0 ? 0 : maxRow + 1;
    }

    /**
     * Build a piece from ASCII art, e.g. {@code fromRows(2, color, "XXX")}.
     * 'X', 'x', '1', '#' are filled; '.', ' ', '0' are empty.
     */
    public static Piece fromRows(int shapeId, int color, String... rows) {
        if (rows.length > GRID) {
            throw new IllegalArgumentException("a piece has at most 5 rows");
        }
        boolean[] mask = new boolean[GRID * GRID];
        for (int row = 0; row < rows.length; row++) {
            String text = rows[row];
            if (text.length() > GRID) {
                throw new IllegalArgumentException("a piece has at most 5 columns");
            }
            for (int col = 0; col < text.length(); col++) {
                char ch = text.charAt(col);
                if (ch == 'X' || ch == 'x' || ch == '1' || ch == '#') {
                    mask[row * GRID + col] = true;
                } else if (ch != '.' && ch != ' ' && ch != '0') {
                    throw new IllegalArgumentException("unexpected character '" + ch + "'");
                }
            }
        }
        return new Piece(shapeId, color, mask);
    }

    public int shapeId() {
        return shapeId;
    }

    /** Opaque ARGB tint, grouping pieces of the same family like Hinh::initShape. */
    public int color() {
        return color;
    }

    /** False outside the 5x5 box. */
    public boolean cellAt(int row, int col) {
        if (row < 0 || row >= GRID || col < 0 || col >= GRID) {
            return false;
        }
        return mask[row * GRID + col];
    }


    public int cellCount() {
        return cellCount;
    }

    /** Width of the filled bounding box in cells (0 when empty). */
    public int width() {
        return width;
    }

    /** Height of the filled bounding box in cells (0 when empty). */
    public int height() {
        return height;
    }

    public boolean isEmpty() {
        return cellCount == 0;
    }
}
