package com.dismal.blockparty.core;

import java.util.Arrays;

public final class Game {

    public static final int TRAY_SLOTS = 3;

    public enum Status { PLAYING, GAME_OVER }

    private final Board board = new Board();
    /** Shape id of the piece that filled each cell, or -1. Drives the board colours. */
    private final int[] shapeAtCell = new int[Board.CELLS];
    private final Piece[] tray = new Piece[TRAY_SLOTS];
    private final boolean[] available = new boolean[TRAY_SLOTS];
    private final Dealer dealer = new Dealer();

    private int score;
    private Status status = Status.PLAYING;

    public Game(long seed) {
        reset(seed);
    }

    public void reset(long seed) {
        board.clear();
        Arrays.fill(shapeAtCell, -1);
        for (int i = 0; i < TRAY_SLOTS; i++) {
            tray[i] = null;
            available[i] = false;
        }
        score = 0;
        status = Status.PLAYING;
        dealer.seed(seed);
        dealTray();
    }

    /** Deal a fixed tray using forced-shape codes (1000 + id); 0 draws randomly. */
    public void dealTrayForced(int... codes) {
        for (int slot = 0; slot < TRAY_SLOTS; slot++) {
            int code = slot < codes.length ? codes[slot] : 0;
            tray[slot] = PieceCatalog.byId(dealer.nextShapeId(code));
            available[slot] = true;
        }
    }

    public Board board() {
        return board;
    }

    public Piece trayPiece(int slot) {
        return slot >= 0 && slot < TRAY_SLOTS ? tray[slot] : null;
    }

    public boolean isAvailable(int slot) {
        return slot >= 0 && slot < TRAY_SLOTS && available[slot];
    }

    public int shapeAt(int row, int col) {
        if (!board.inside(row, col)) {
            return -1;
        }
        return shapeAtCell[row * Board.SIZE + col];
    }

    public int score() {
        return score;
    }

    public Status status() {
        return status;
    }

    public boolean isGameOver() {
        return status == Status.GAME_OVER;
    }

    /** True when the piece fits with its 5x5 anchor at (row, col). */
    public boolean fits(Piece piece, int row, int col) {
        if (piece == null || piece.isEmpty()) {
            return false;
        }
        for (int r = 0; r < Piece.GRID; r++) {
            for (int c = 0; c < Piece.GRID; c++) {
                if (!piece.cellAt(r, c)) {
                    continue;
                }
                int boardRow = row + r;
                int boardCol = col + c;
                if (!board.inside(boardRow, boardCol)) {
                    return false;
                }
                if (board.filled(boardRow, boardCol)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean canPlace(int slot, int row, int col) {
        if (status != Status.PLAYING || !isAvailable(slot)) {
            return false;
        }
        return fits(tray[slot], row, col);
    }

    public boolean canPlaceAnywhere(Piece piece) {
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (fits(piece, row, col)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Game over only when no remaining tray piece fits anywhere. */
    public boolean hasMove() {
        for (int slot = 0; slot < TRAY_SLOTS; slot++) {
            if (available[slot] && canPlaceAnywhere(tray[slot])) {
                return true;
            }
        }
        return false;
    }

    /** Commit a placement. Returns {placed == false} for an illegal or finished game. */
    public PlacementResult place(int slot, int row, int col) {
        PlacementResult result = new PlacementResult();
        if (!canPlace(slot, row, col)) {
            return result;
        }

        Piece piece = tray[slot];
        int shapeId = piece.shapeId();
        int cells = piece.cellCount();

        for (int r = 0; r < Piece.GRID; r++) {
            for (int c = 0; c < Piece.GRID; c++) {
                if (piece.cellAt(r, c)) {
                    int boardRow = row + r;
                    int boardCol = col + c;
                    board.set(boardRow, boardCol, true);
                    shapeAtCell[boardRow * Board.SIZE + boardCol] = shapeId;
                }
            }
        }
        available[slot] = false;

        result.placed = true;
        result.shapeId = shapeId;

        if (trayEmpty()) {
            dealTray();
        }

        result.rows = board.fullRows();
        result.columns = board.fullColumns();
        result.linesCleared = result.rows.length + result.columns.length;
        result.baseScore = Scoring.placementScore(cells, result.linesCleared);
        result.multiplier = result.linesCleared > 0
                ? Scoring.comboMultiplier(shapeId, result.linesCleared)
                : 1;
        result.gained = result.baseScore * result.multiplier;

        clearLines(result.rows, result.columns);
        score += result.gained;
        updateStatus();
        return result;
    }

    private boolean trayEmpty() {
        for (int slot = 0; slot < TRAY_SLOTS; slot++) {
            if (available[slot]) {
                return false;
            }
        }
        return true;
    }

    /** MainGame::miniBlocksInit: a tray holds at most one 3x3 giant. */
    private void dealTray() {
        int first = dealer.nextShapeId(false);
        int second = dealer.nextShapeId(first == PieceCatalog.GIANT_SHAPE_ID);
        int third = dealer.nextShapeId(first == PieceCatalog.GIANT_SHAPE_ID
                || second == PieceCatalog.GIANT_SHAPE_ID);

        tray[0] = PieceCatalog.byId(first);
        tray[1] = PieceCatalog.byId(second);
        tray[2] = PieceCatalog.byId(third);
        available[0] = true;
        available[1] = true;
        available[2] = true;
    }

    private void clearLines(int[] rows, int[] columns) {
        for (int row : rows) {
            for (int col = 0; col < Board.SIZE; col++) {
                clearCell(row, col);
            }
        }
        for (int col : columns) {
            for (int row = 0; row < Board.SIZE; row++) {
                clearCell(row, col);
            }
        }
    }

    private void clearCell(int row, int col) {
        board.set(row, col, false);
        shapeAtCell[row * Board.SIZE + col] = -1;
    }

    private void updateStatus() {
        if (status == Status.GAME_OVER) {
            return;
        }
        if (!hasMove()) {
            status = Status.GAME_OVER;
        }
    }

    /**
     * Install a state directly (save/load, tests) and re-evaluate game over.
     *
     * @param shapeIds 100 entries, shape id per cell or -1 for empty
     */
    public void restoreState(Board sourceBoard, int[] shapeIds, Piece[] pieces,
                             boolean[] slotAvailable, int restoredScore) {
        board.clear();
        Arrays.fill(shapeAtCell, -1);
        if (sourceBoard != null) {
            for (int row = 0; row < Board.SIZE; row++) {
                for (int col = 0; col < Board.SIZE; col++) {
                    if (sourceBoard.filled(row, col)) {
                        board.set(row, col, true);
                    }
                }
            }
        }
        if (shapeIds != null) {
            System.arraycopy(shapeIds, 0, shapeAtCell, 0, Math.min(shapeIds.length, Board.CELLS));
        }
        // Only filled cells can carry a colour, and an unknown id falls back to the
        // view's neutral tint.
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                int index = row * Board.SIZE + col;
                if (!board.filled(row, col) || !PieceCatalog.isValidId(shapeAtCell[index])) {
                    shapeAtCell[index] = -1;
                }
            }
        }
        for (int slot = 0; slot < TRAY_SLOTS; slot++) {
            tray[slot] = pieces != null && slot < pieces.length ? pieces[slot] : null;
            available[slot] = tray[slot] != null
                    && (slotAvailable == null || (slot < slotAvailable.length && slotAvailable[slot]));
        }
        score = Math.max(0, restoredScore);
        status = Status.PLAYING;
        updateStatus();
    }
}
