package com.dismal.blockparty.core;
public final class PieceCatalog {

    /** Id of the largest piece (the 3x3); a tray never holds two of them. */
    public static final int GIANT_SHAPE_ID = 18;

    private static final int SINGLE = 0xFF6B78CC;
    private static final int BAR2 = 0xFFFDBB31;
    private static final int BAR3 = 0xFFE6833B;
    private static final int BAR4 = 0xFFDE516F;
    private static final int BAR5 = 0xFFD14E44;
    private static final int TRIOMINO = 0xFF4CC373;
    private static final int PENTOMINO = 0xFF4EB1DD;
    private static final int SQUARE4 = 0xFF89D844;
    private static final int SQUARE9 = 0xFF43CEA0;

    private static final Piece[] PIECES = {
            Piece.fromRows(0, SINGLE, "X"),
            Piece.fromRows(1, BAR2, "XX"),
            Piece.fromRows(2, BAR3, "XXX"),
            Piece.fromRows(3, BAR4, "XXXX"),
            Piece.fromRows(4, BAR5, "XXXXX"),
            Piece.fromRows(5, BAR2, "X", "X"),
            Piece.fromRows(6, BAR3, "X", "X", "X"),
            Piece.fromRows(7, BAR4, "X", "X", "X", "X"),
            Piece.fromRows(8, BAR5, "X", "X", "X", "X", "X"),
            Piece.fromRows(9, TRIOMINO, "XX", "X."),
            Piece.fromRows(10, TRIOMINO, "XX", ".X"),
            Piece.fromRows(11, TRIOMINO, ".X", "XX"),
            Piece.fromRows(12, TRIOMINO, "X.", "XX"),
            Piece.fromRows(13, PENTOMINO, "XXX", "X..", "X.."),
            Piece.fromRows(14, PENTOMINO, "XXX", "..X", "..X"),
            Piece.fromRows(15, PENTOMINO, "X..", "X..", "XXX"),
            Piece.fromRows(16, PENTOMINO, "..X", "..X", "XXX"),
            Piece.fromRows(17, SQUARE4, "XX", "XX"),
            Piece.fromRows(18, SQUARE9, "XXX", "XXX", "XXX"),
    };

    private PieceCatalog() {
    }

    public static int count() {
        return PIECES.length;
    }

    public static Piece byId(int shapeId) {
        if (shapeId < 0 || shapeId >= PIECES.length) {
            throw new IllegalArgumentException("unknown shape id " + shapeId);
        }
        return PIECES[shapeId];
    }

    public static boolean isValidId(int shapeId) {
        return shapeId >= 0 && shapeId < PIECES.length;
    }
}
