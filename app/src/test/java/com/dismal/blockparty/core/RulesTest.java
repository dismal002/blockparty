package com.dismal.blockparty.core;

/**
 * JVM-only checks for the ported rules. No test framework: run {@code main} and a
 * non-zero exit status means something failed (mirrors block_game/tests/rules_tests.cpp).
 */
public final class RulesTest {

    private static int failures = 0;

    private static void check(boolean condition, String what) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    public static void main(String[] args) {
        catalogueMatchesReference();
        lrand48MatchesGlibc();
        dealerFollowsInitShape();
        dealerKeepsTraysPlayable();
        boardLineDetection();
        placementBoundsAndOverlap();
        placementConsumesSlotAndRefills();
        rowAndColumnClearScoring();
        comboMultiplierApplies();
        comboMultiplierTable();
        gameOverDetection();
        saveRoundTrip();

        if (failures == 0) {
            System.out.println("all checks passed");
            return;
        }
        System.out.println(failures + " check(s) failed");
        System.exit(1);
    }

    // --- the piece table -----------------------------------------------------

    private static void catalogueMatchesReference() {
        check(PieceCatalog.count() == 19, "the catalogue has 19 shapes");

        int[] cells = {1, 2, 3, 4, 5, 2, 3, 4, 5, 3, 3, 3, 3, 5, 5, 5, 5, 4, 9};
        int[] heights = {1, 1, 1, 1, 1, 2, 3, 4, 5, 2, 2, 2, 2, 3, 3, 3, 3, 2, 3};
        int[] widths = {1, 2, 3, 4, 5, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 2, 3};

        for (int id = 0; id < 19; id++) {
            Piece piece = PieceCatalog.byId(id);
            String at = "shape " + id;
            check(piece.shapeId() == id, at + " keeps its id");
            check(piece.cellCount() == cells[id], at + " cell count");
            check(piece.height() == heights[id], at + " height");
            check(piece.width() == widths[id], at + " width");
        }

        Piece single = PieceCatalog.byId(0);
        check(single.cellAt(0, 0) && single.cellCount() == 1, "shape 0 is a single cell");

        Piece bar5 = PieceCatalog.byId(4);
        check(bar5.cellAt(0, 4) && !bar5.cellAt(1, 0), "shape 4 is a horizontal 5-bar");

        Piece tall5 = PieceCatalog.byId(8);
        check(tall5.cellAt(4, 0) && !tall5.cellAt(0, 1), "shape 8 is a vertical 5-bar");

        Piece sTrio = PieceCatalog.byId(10);
        check(sTrio.cellAt(0, 0) && sTrio.cellAt(0, 1) && sTrio.cellAt(1, 1) && !sTrio.cellAt(1, 0),
                "shape 10 is the S triomino");

        Piece o4 = PieceCatalog.byId(17);
        check(o4.cellCount() == 4 && o4.width() == 2 && o4.height() == 2, "shape 17 is the O tetromino");

        Piece giant = PieceCatalog.byId(PieceCatalog.GIANT_SHAPE_ID);
        check(giant.cellCount() == 9 && giant.width() == 3 && giant.height() == 3, "the giant is the 3x3");
        check(giant.cellAt(2, 2) && !giant.cellAt(0, 3), "the giant fills its 3x3 box");

        int largest = 0;
        for (int id = 0; id < 19; id++) {
            largest = Math.max(largest, PieceCatalog.byId(id).cellCount());
        }
        check(largest == 9 && giant.cellCount() == largest, "the giant is the largest piece");

        check(PieceCatalog.byId(2).color() == PieceCatalog.byId(6).color(), "3-bars share a colour");
        check(PieceCatalog.byId(4).color() == PieceCatalog.byId(8).color(), "5-bars share a colour");
        check(PieceCatalog.byId(13).color() == PieceCatalog.byId(16).color(), "pentominoes share a colour");
        check(PieceCatalog.byId(0).color() == 0xFF6B78CC, "single-cell colour");
        check(giant.color() == 0xFF43CEA0, "giant colour");
        check(PieceCatalog.byId(1).color() != PieceCatalog.byId(2).color(), "different families differ");
    }

    // --- dealing -------------------------------------------------------------

    private static void lrand48MatchesGlibc() {
        Dealer fresh = new Dealer();
        check(fresh.next() == 0, "unseeded first draw is 0");
        check(fresh.next() == 2116118, "unseeded second draw");
        check(fresh.next() == 89401895, "unseeded third draw");

        Dealer a = new Dealer();
        a.seed(12345);
        check(a.next() == 483889296, "srand48(12345) draw 1");
        check(a.next() == 1973930609, "srand48(12345) draw 2");
        check(a.next() == 444188209, "srand48(12345) draw 3");

        Dealer b = new Dealer();
        b.seed(1);
        check(b.next() == 89400484, "srand48(1) draw 1");
    }

    private static void dealerFollowsInitShape() {
        Game seeded = new Game(12345);
        check(seeded.trayPiece(0).shapeId() == 13, "seed 12345 slot 0");
        check(seeded.trayPiece(1).shapeId() == 13, "seed 12345 slot 1");
        check(seeded.trayPiece(2).shapeId() == 15, "seed 12345 slot 2");

        Game seven = new Game(7);
        check(seven.trayPiece(0).shapeId() == 11, "seed 7 slot 0");
        check(seven.trayPiece(1).shapeId() == 6, "seed 7 slot 1");
        check(seven.trayPiece(2).shapeId() == 3, "seed 7 slot 2");

        Dealer avoider = new Dealer();
        avoider.seed(99);
        for (int draw = 0; draw < 500; draw++) {
            int id = avoider.nextShapeId(true);
            check(id >= 0 && id < PieceCatalog.GIANT_SHAPE_ID, "avoiding draws stay below the giant id");
        }

        Dealer forced = new Dealer();
        check(forced.nextShapeId(1000 + 6) == 6, "code 1006 forces shape 6");
        check(forced.nextShapeId(1000 + 14) == 14, "code 1014 forces shape 14");
        check(forced.nextShapeId(1000 + PieceCatalog.GIANT_SHAPE_ID) == PieceCatalog.GIANT_SHAPE_ID,
                "code 1018 forces the giant");

        Dealer consumer = new Dealer();
        check(consumer.nextShapeId(1000 + 6) == 6, "forced draw returns the forced shape");
        check(consumer.next() == 2116118, "a forced draw still advances the generator");

        Game tutorial = new Game(1);
        tutorial.dealTrayForced(1000, 1000 + 6, 1000 + 14);
        check(tutorial.trayPiece(0).shapeId() == 0, "forced tray slot 0");
        check(tutorial.trayPiece(1).shapeId() == 6, "forced tray slot 1");
        check(tutorial.trayPiece(2).shapeId() == 14, "forced tray slot 2");
    }

    private static void dealerKeepsTraysPlayable() {
        Dealer dealer = new Dealer();
        dealer.seed(12345);
        for (int round = 0; round < 200; round++) {
            int first = dealer.nextShapeId(false);
            int second = dealer.nextShapeId(first == PieceCatalog.GIANT_SHAPE_ID);
            int third = dealer.nextShapeId(first == PieceCatalog.GIANT_SHAPE_ID
                    || second == PieceCatalog.GIANT_SHAPE_ID);
            int giants = (first == PieceCatalog.GIANT_SHAPE_ID ? 1 : 0)
                    + (second == PieceCatalog.GIANT_SHAPE_ID ? 1 : 0)
                    + (third == PieceCatalog.GIANT_SHAPE_ID ? 1 : 0);
            check(giants <= 1, "a tray never holds two giant pieces");
        }

        Game a = new Game(777);
        Game b = new Game(777);
        for (int slot = 0; slot < Game.TRAY_SLOTS; slot++) {
            check(a.trayPiece(slot).shapeId() == b.trayPiece(slot).shapeId(), "the same seed deals the same tray");
        }
    }

    // --- board ---------------------------------------------------------------

    private static void boardLineDetection() {
        Board board = new Board();
        check(board.filledCount() == 0, "new board is empty");

        for (int col = 0; col < Board.SIZE; col++) {
            board.set(3, col, true);
        }
        check(board.rowFull(3), "row 3 is full");
        check(board.filledCount() == 10, "row fill counts 10 cells");
        check(board.fullRows().length == 1 && board.fullRows()[0] == 3, "fullRows reports row 3");
        check(board.fullColumns().length == 0, "no full columns yet");

        for (int row = 0; row < Board.SIZE; row++) {
            board.set(row, 7, true);
        }
        check(board.columnFull(7), "column 7 is full");

        board.clearLines(board.fullRows(), board.fullColumns());
        check(board.filledCount() == 0, "clearing a full row and column empties the board");

        board.set(-1, 0, true);
        board.set(0, 10, true);
        check(board.filledCount() == 0, "out-of-range writes are ignored");
        check(!board.filled(0, -1), "out-of-range reads are empty");
    }

    // --- placement -----------------------------------------------------------

    private static Piece[] tray(Piece first) {
        Piece[] pieces = new Piece[Game.TRAY_SLOTS];
        pieces[0] = first;
        return pieces;
    }

    private static boolean[] onlyFirst() {
        boolean[] available = new boolean[Game.TRAY_SLOTS];
        available[0] = true;
        return available;
    }

    private static void placementBoundsAndOverlap() {
        Piece bar = PieceCatalog.byId(4);
        Game game = new Game(1);
        game.restoreState(new Board(), new int[Board.CELLS], tray(bar), onlyFirst(), 0);

        check(game.canPlace(0, 0, 0), "bar fits at the top-left corner");
        check(game.canPlace(0, 0, 5), "bar fits flush against the right edge");
        check(!game.canPlace(0, 0, 6), "bar hangs off the right edge");
        check(game.canPlace(0, 9, 0), "bar still fits on the last row");
        check(!game.canPlace(0, 10, 0), "anchor past the last row is rejected");
        check(!game.canPlace(0, -1, 0), "negative anchor is rejected");
        check(!game.canPlace(1, 0, 0), "used slot cannot be placed");

        Game tall = new Game(1);
        tall.restoreState(new Board(), new int[Board.CELLS], tray(PieceCatalog.byId(8)), onlyFirst(), 0);
        check(tall.canPlace(0, 5, 0), "tall piece fits ending on the last row");
        check(!tall.canPlace(0, 6, 0), "tall piece hangs off the bottom edge");

        Board blocked = new Board();
        blocked.set(0, 2, true);
        Game overlap = new Game(1);
        overlap.restoreState(blocked, new int[Board.CELLS], tray(bar), onlyFirst(), 0);
        check(!overlap.canPlace(0, 0, 0), "bar cannot overlap a filled cell");
        check(overlap.canPlace(0, 1, 0), "bar fits one row lower");

        PlacementResult result = overlap.place(0, 0, 0);
        check(!result.placed, "overlapping move is refused");
        check(overlap.board().filledCount() == 1, "refused move leaves the board untouched");
    }

    private static void placementConsumesSlotAndRefills() {
        Game game = new Game(4);
        check(game.isAvailable(0) && game.isAvailable(1) && game.isAvailable(2), "a fresh tray has three pieces");

        PlacementResult first = game.place(0, 0, 0);
        check(first.placed, "first placement succeeds");
        check(!game.isAvailable(0), "slot 0 is used after placement");
        check(game.board().filledCount() == PieceCatalog.byId(first.shapeId).cellCount(),
                "board holds exactly the piece's cells");

        Game noLines = new Game(5);
        int before = noLines.score();
        PlacementResult quiet = noLines.place(0, 0, 0);
        check(quiet.linesCleared == 0, "opening move clears nothing");
        check(quiet.multiplier == 1, "no lines means no multiplier");
        check(noLines.score() == before + quiet.baseScore, "score grows by the base score");
        check(PieceCatalog.isValidId(noLines.trayPiece(0).shapeId()), "refilled slot holds a valid shape");

        // Using all three slots redeals, and the refill happens before lines clear.
        Game refill = new Game(11);
        for (int slot = 0; slot < Game.TRAY_SLOTS; slot++) {
            boolean placed = false;
            for (int row = 0; row < Board.SIZE && !placed; row++) {
                for (int col = 0; col < Board.SIZE && !placed; col++) {
                    if (refill.canPlace(slot, row, col)) {
                        placed = refill.place(slot, row, col).placed;
                    }
                }
            }
            check(placed, "every tray piece fits an empty board");
        }
        check(refill.isAvailable(0) && refill.isAvailable(1) && refill.isAvailable(2),
                "using the last piece redeals the tray");
    }

    // --- clearing and scoring ------------------------------------------------

    private static void rowAndColumnClearScoring() {
        Board board = new Board();
        for (int col = 0; col < 9; col++) {
            board.set(0, col, true);
        }
        for (int row = 1; row < Board.SIZE; row++) {
            board.set(row, 0, true);
        }

        Game game = new Game(1);
        game.restoreState(board, new int[Board.CELLS], tray(PieceCatalog.byId(0)), onlyFirst(), 0);

        PlacementResult result = game.place(0, 0, 9);
        check(result.placed, "1x1 completes the last cell");
        check(result.linesCleared == 2, "one row and one column clear together");
        check(result.baseScore == 1 + 2 * Scoring.CELLS_PER_LINE, "base score is cells + 10 per line");
        check(result.multiplier == 1, "the 1x1 never combos");
        check(result.gained == 21, "1 cell and 2 lines score 21");
        check(game.score() == 21, "score matches the award");
        check(game.board().filledCount() == 0, "cleared row and column are emptied");

        // Same board, with the shape id recorded per cell, clears shape ids too.
        check(game.shapeAt(0, 0) == -1, "cleared cells forget their shape id");
    }

    private static void comboMultiplierApplies() {
        Board board = new Board();
        for (int row = 0; row < 3; row++) {
            for (int col = 3; col < Board.SIZE; col++) {
                board.set(row, col, true);
            }
        }

        Game game = new Game(2);
        game.restoreState(board, new int[Board.CELLS],
                tray(PieceCatalog.byId(PieceCatalog.GIANT_SHAPE_ID)), onlyFirst(), 0);

        PlacementResult result = game.place(0, 0, 0);
        check(result.linesCleared == 3, "the 3x3 completes three rows");
        check(result.baseScore == 9 + 3 * 10, "base score is 39");
        check(result.multiplier == 2, "three lines double the award for the giant");
        check(result.gained == 78, "3x3 triple clear scores 78");
        check(game.board().filledCount() == 0, "all three rows are cleared");

        // A placement that does not clear records the shape id behind each cell.
        Game recorder = new Game(1);
        recorder.restoreState(new Board(), new int[Board.CELLS],
                tray(PieceCatalog.byId(17)), onlyFirst(), 0);
        recorder.place(0, 4, 4);
        check(recorder.shapeAt(4, 4) == 17 && recorder.shapeAt(5, 5) == 17,
                "placed cells record their shape id");
        check(recorder.shapeAt(0, 0) == -1, "empty cells carry no shape id");
    }

    private static void comboMultiplierTable() {
        check(Scoring.comboMultiplier(0, 9) == 1, "shape 0 never combos");
        check(Scoring.comboMultiplier(1, 2) == 1, "shape 1 below its threshold keeps x1");
        check(Scoring.comboMultiplier(1, 3) == 2, "shape 1 doubles at three lines");
        check(Scoring.comboMultiplier(2, 4) == 3, "medium shapes triple at four lines");
        check(Scoring.comboMultiplier(2, 5) == 3, "medium shapes cap at x3");
        check(Scoring.comboMultiplier(17, 2) == 2, "shape 17 doubles at two lines");
        check(Scoring.comboMultiplier(17, 3) == 3, "shape 17 triples at three lines");
        check(Scoring.comboMultiplier(18, 3) == 2, "the giant doubles at three lines");
        check(Scoring.comboMultiplier(18, 6) == 5, "the giant reaches x5 at six lines");
        check(Scoring.comboMultiplier(18, 9) == 5, "the giant saturates at x5");
        check(Scoring.comboMultiplier(99, 4) == 1, "unknown shape ids fall back to x1");
        check(Scoring.comboMultiplier(15, 0) == 1, "no lines means no multiplier");

        check(Scoring.placementScore(5, 0) == 5, "placementScore adds no lines");
        check(Scoring.placementScore(5, 2) == 25, "placementScore adds 10 per line");
        check(Scoring.scorePlacement(18, 9, 3) == 78, "scorePlacement multiplies the base");
        check(Scoring.scorePlacement(0, 1, 3) == 31, "scorePlacement without a combo bonus");
    }

    // --- game over -----------------------------------------------------------

    private static void gameOverDetection() {
        Board crowded = new Board();
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                crowded.set(row, col, true);
            }
        }
        crowded.set(9, 9, false);

        Game stuck = new Game(3);
        stuck.restoreState(crowded, new int[Board.CELLS], tray(PieceCatalog.byId(1)), onlyFirst(), 0);
        check(stuck.status() == Game.Status.GAME_OVER, "a domino cannot fit one hole");
        check(!stuck.hasMove(), "no move is reported");
        check(!stuck.canPlace(0, 9, 9), "placing is refused once the game is over");
        check(!stuck.place(0, 9, 9).placed, "place() reports the refusal");

        Game alive = new Game(3);
        alive.restoreState(crowded, new int[Board.CELLS], tray(PieceCatalog.byId(0)), onlyFirst(), 0);
        check(alive.status() == Game.Status.PLAYING, "a 1x1 still fits the hole");
        check(alive.hasMove(), "a move is reported");

        PlacementResult result = alive.place(0, 9, 9);
        check(result.placed, "the 1x1 can be placed");
        check(result.linesCleared == 2 * Board.SIZE, "filling the hole completes all lines");
        check(alive.board().filledCount() == 0, "twenty cleared lines empty the board");
    }

    // --- persistence ---------------------------------------------------------

    private static void saveRoundTrip() {
        Game original = new Game(9);
        for (int move = 0; move < 6 && !original.isGameOver(); move++) {
            boolean placed = false;
            for (int slot = 0; slot < Game.TRAY_SLOTS && !placed; slot++) {
                for (int row = 0; row < Board.SIZE && !placed; row++) {
                    for (int col = 0; col < Board.SIZE && !placed; col++) {
                        if (original.canPlace(slot, row, col)) {
                            placed = original.place(slot, row, col).placed;
                        }
                    }
                }
            }
        }

        String blob = GameSave.encode(original);
        GameSave.State state = GameSave.decode(blob);
        check(state != null, "decode accepts its own blob");
        if (state == null) {
            return;
        }
        check(state.score == original.score(), "score survives the round trip");

        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                check(original.board().filled(row, col) == state.board.filled(row, col),
                        "board cell survives the round trip");
                check(original.shapeAt(row, col) == state.shapeAtCell[row * Board.SIZE + col],
                        "cell colour survives the round trip");
            }
        }
        for (int slot = 0; slot < Game.TRAY_SLOTS; slot++) {
            check(original.isAvailable(slot) == state.available[slot], "tray availability survives");
            check(!state.available[slot]
                            || original.trayPiece(slot).shapeId() == state.tray[slot].shapeId(),
                    "tray shape survives");
        }

        Game restored = new Game(1);
        restored.restoreState(state.board, state.shapeAtCell, state.tray, state.available, state.score);
        check(restored.score() == original.score(), "restored game keeps its score");
        check(restored.shapeAt(0, 0) == original.shapeAt(0, 0), "restored game keeps cell colours");

        check(GameSave.decode("nonsense") == null, "short blob is rejected");
        check(GameSave.decode(null) == null, "null blob is rejected");
        String badCell = blob.substring(0, 4) + "zz" + blob.substring(6);
        check(GameSave.decode(badCell) == null, "non-hex cell is rejected");
        String badShape = "BB1;0;" + blob.split(";", -1)[2] + ";1f";
        check(GameSave.decode(badShape) == null, "unknown shape id is rejected");
    }
}
