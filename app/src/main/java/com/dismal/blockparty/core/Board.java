package com.dismal.blockparty.core;

import java.util.Arrays;

public final class Board {

    public static final int SIZE = 10;
    public static final int CELLS = SIZE * SIZE;

    private final boolean[] cells = new boolean[CELLS];

    public void clear() {
        Arrays.fill(cells, false);
    }

    public boolean inside(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    public boolean filled(int row, int col) {
        if (!inside(row, col)) {
            return false;
        }
        return cells[row * SIZE + col];
    }

    public void set(int row, int col, boolean value) {
        if (!inside(row, col)) {
            return;
        }
        cells[row * SIZE + col] = value;
    }

    public boolean rowFull(int row) {
        if (row < 0 || row >= SIZE) {
            return false;
        }
        for (int col = 0; col < SIZE; col++) {
            if (!cells[row * SIZE + col]) {
                return false;
            }
        }
        return true;
    }

    public boolean columnFull(int col) {
        if (col < 0 || col >= SIZE) {
            return false;
        }
        for (int row = 0; row < SIZE; row++) {
            if (!cells[row * SIZE + col]) {
                return false;
            }
        }
        return true;
    }

    public int filledCount() {
        int count = 0;
        for (boolean cell : cells) {
            if (cell) {
                count++;
            }
        }
        return count;
    }

    /** Indices of completely filled rows, ascending. */
    public int[] fullRows() {
        int[] rows = new int[SIZE];
        int found = 0;
        for (int row = 0; row < SIZE; row++) {
            if (rowFull(row)) {
                rows[found++] = row;
            }
        }
        return Arrays.copyOf(rows, found);
    }

    /** Indices of completely filled columns, ascending. */
    public int[] fullColumns() {
        int[] columns = new int[SIZE];
        int found = 0;
        for (int col = 0; col < SIZE; col++) {
            if (columnFull(col)) {
                columns[found++] = col;
            }
        }
        return Arrays.copyOf(columns, found);
    }

    /** Empties every cell of the given rows and columns. */
    public void clearLines(int[] rows, int[] columns) {
        for (int row : rows) {
            for (int col = 0; col < SIZE; col++) {
                set(row, col, false);
            }
        }
        for (int col : columns) {
            for (int row = 0; row < SIZE; row++) {
                set(row, col, false);
            }
        }
    }
}
