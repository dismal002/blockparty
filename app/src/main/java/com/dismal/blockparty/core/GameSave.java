package com.dismal.blockparty.core;

public final class GameSave {

    private static final String MAGIC = "BB1";
    private static final int CELL_CHARS = Board.CELLS * 2;

    private GameSave() {
    }

    public static final class State {
        public final Board board;
        public final int[] shapeAtCell;
        public final Piece[] tray;
        public final boolean[] available;
        public final int score;

        State(Board board, int[] shapeAtCell, Piece[] tray, boolean[] available, int score) {
            this.board = board;
            this.shapeAtCell = shapeAtCell;
            this.tray = tray;
            this.available = available;
            this.score = score;
        }
    }

    public static String encode(Game game) {
        StringBuilder out = new StringBuilder(12 + CELL_CHARS + Game.TRAY_SLOTS * 2);
        out.append(MAGIC).append(';').append(game.score()).append(';');

        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                int shapeId = game.board().filled(row, col) ? game.shapeAt(row, col) : -1;
                appendTwoHex(out, shapeId < 0 ? 0 : shapeId + 1);
            }
        }

        out.append(';');
        for (int slot = 0; slot < Game.TRAY_SLOTS; slot++) {
            Piece piece = game.trayPiece(slot);
            boolean live = game.isAvailable(slot) && piece != null;
            appendTwoHex(out, live ? piece.shapeId() + 1 : 0);
        }
        return out.toString();
    }

    /** Returns null when the text is missing, corrupt or from another format. */
    public static State decode(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(";", -1);
        if (parts.length != 4 || !MAGIC.equals(parts[0])) {
            return null;
        }

        int score;
        try {
            score = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (score < 0) {
            return null;
        }

        String cells = parts[2];
        if (cells.length() != CELL_CHARS) {
            return null;
        }

        Board board = new Board();
        int[] shapeAtCell = new int[Board.CELLS];
        for (int i = 0; i < Board.CELLS; i++) {
            int code = parseTwoHex(cells, i * 2);
            if (code < 0) {
                return null;
            }
            if (code == 0) {
                shapeAtCell[i] = -1;
                continue;
            }
            int shapeId = code - 1;
            if (!PieceCatalog.isValidId(shapeId)) {
                return null;
            }
            shapeAtCell[i] = shapeId;
            board.set(i / Board.SIZE, i % Board.SIZE, true);
        }

        String slots = parts[3];
        if (slots.length() != Game.TRAY_SLOTS * 2) {
            return null;
        }
        Piece[] tray = new Piece[Game.TRAY_SLOTS];
        boolean[] available = new boolean[Game.TRAY_SLOTS];
        for (int slot = 0; slot < Game.TRAY_SLOTS; slot++) {
            int code = parseTwoHex(slots, slot * 2);
            if (code < 0) {
                return null;
            }
            if (code == 0) {
                continue;
            }
            int shapeId = code - 1;
            if (!PieceCatalog.isValidId(shapeId)) {
                return null;
            }
            tray[slot] = PieceCatalog.byId(shapeId);
            available[slot] = true;
        }

        return new State(board, shapeAtCell, tray, available, score);
    }

    private static void appendTwoHex(StringBuilder out, int value) {
        out.append(Character.forDigit((value >> 4) & 0xF, 16));
        out.append(Character.forDigit(value & 0xF, 16));
    }

    private static int parseTwoHex(String text, int at) {
        int high = Character.digit(text.charAt(at), 16);
        int low = Character.digit(text.charAt(at + 1), 16);
        if (high < 0 || low < 0) {
            return -1;
        }
        return (high << 4) | low;
    }
}
