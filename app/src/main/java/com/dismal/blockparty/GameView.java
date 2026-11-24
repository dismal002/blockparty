package com.dismal.blockparty;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import com.dismal.blockparty.core.Board;
import com.dismal.blockparty.core.Game;
import com.dismal.blockparty.core.GameSave;
import com.dismal.blockparty.core.Piece;
import com.dismal.blockparty.core.PieceCatalog;
import com.dismal.blockparty.core.PlacementResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The whole game surface: score header, 10x10 board, three-slot tray, drag and drop,
 * clear/score animations and the game-over panel. Everything is drawn with Canvas, so
 * the app needs no image assets.
 */
public final class GameView extends View {

    private static final String PREFS = "block_blitz";
    private static final String KEY_STATE = "state";
    private static final String KEY_BEST = "best";
    private static final String KEY_MUTED = "muted";

    // Cool porcelain palette: white cards on a soft blue-grey mist, deep teal accents.
    // the cool blue/violet look of the games this genre is known for.
    private static final int BACKGROUND_TOP = 0xFFF7FAFC;
    private static final int BACKGROUND_BOTTOM = 0xFFDDE8F0;
    private static final int BOARD_PANEL = 0xFFFFFFFF;
    private static final int TRAY_PANEL = 0xFFFFFFFF;
    private static final int SLOT_PANEL = 0xFFF0F5FA;
    private static final int EMPTY_CELL = 0xFFE8EFF5;
    private static final int TEXT_PRIMARY = 0xFF1C2B36;
    private static final int TEXT_DIM = 0xFF6C808D;
    private static final int ACCENT = 0xFF0F6E7D;
    private static final int RESTORED_COLOR = 0xFFA3B7C2;

    // Clearing releases tiles one at a time in a wave (bottom row first, then
    // upward, left to right). Each tile slides one cell to the right while it fades
    // out, and a star sparkle blooms where it was and lingers briefly.
    private static final long CLEAR_STEP_MS = 55L;
    private static final long SLIDE_MS = 170L;
    private static final long SPARK_DELAY_MS = 125L;
    private static final long SPARK_MS = 330L;
    private static final long POP_MS = 190L;
    private static final long FLOAT_MS = 900L;
    private static final long COMBO_MS = 950L;
    private static final long GAME_OVER_MS = 340L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF scratch = new RectF();
    private final RectF blockRect = new RectF();
    private final RectF glossRect = new RectF();
    private final RectF boardRect = new RectF();
    private final RectF trayRect = new RectF();
    private final RectF[] slotRects = {new RectF(), new RectF(), new RectF()};
    private final RectF newGameRect = new RectF();
    private final RectF playAgainRect = new RectF();
    private final RectF muteRect = new RectF();
    private final RectF arcRect = new RectF();
    private final Path speakerPath = new Path();
    private final Path starPath = new Path();
    private final Path sparklePath = new Path();
    private final Path clipPath = new Path();
    private final Path topBevel = new Path();
    private final Path leftBevel = new Path();
    private final Path bottomBevel = new Path();
    private final Path rightBevel = new Path();
    private final RectF faceRect = new RectF();

    private final float density;
    private final float scaledDensity;
    private final SharedPreferences prefs;
    private final Typeface blackFace;
    private final Typeface boldFace;
    private final Typeface mediumFace;

    private final SoundEngine sound = new SoundEngine();
    private boolean muted;

    private Game game;
    private int bestScore;
    private final int[] cellColor = new int[Board.CELLS];
    private final long[] popAt = new long[Board.CELLS];
    private float shownScore;
    private long lastFrameAt;

    private float pad;
    private float headerTop;
    private float headerHeight;
    private float boardLeft;
    private float boardTop;
    private float boardSize;
    private float cellSize;
    private float trayTop;
    private float trayHeight;
    private float buttonTop;
    private float buttonHeight;
    private Shader backgroundShader;
    private static final int GLOW_STEPS = 8;
    private Shader[] sparkleGlows;

    private int dragSlot = -1;
    private int activePointerId = -1;
    private float dragX;
    private float dragY;
    private float dragLift;
    private int hoverRow = -1;
    private int hoverCol = -1;
    private boolean hoverValid;

    private final List<ClearFlash> flashes = new ArrayList<>();
    private final List<FloatText> floats = new ArrayList<>();
    private long comboAt;
    private int comboMultiplier;
    private float gameOverProgress;
    private boolean newBestThisGame;

    private static final class ClearFlash {
        final long at;
        final boolean[] cells = new boolean[Board.CELLS];
        final int[] colors = new int[Board.CELLS];
        final long[] delay = new long[Board.CELLS];
        long duration;

        ClearFlash(long at) {
            this.at = at;
        }
    }

    private static final class FloatText {
        final long at;
        final String text;
        final float x;
        final float y;
        final int color;
        final float size;

        FloatText(long at, String text, float x, float y, int color, float size) {
            this.at = at;
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.size = size;
        }
    }

    public GameView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        blackFace = Typeface.create("sans-serif-black", Typeface.NORMAL);
        boldFace = Typeface.create("sans-serif", Typeface.BOLD);
        mediumFace = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        bestScore = prefs.getInt(KEY_BEST, 0);
        muted = prefs.getBoolean(KEY_MUTED, false);
        sound.setMuted(muted);
        game = restoreGame();
        if (game == null) {
            game = new Game(System.currentTimeMillis() / 1000L);
        }
        restoreColors();
        shownScore = game.score();
        setFocusable(true);
    }

    // --- state ---------------------------------------------------------------

    private Game restoreGame() {
        GameSave.State state = GameSave.decode(prefs.getString(KEY_STATE, null));
        if (state == null) {
            return null;
        }
        Game restored = new Game(1L);
        restored.restoreState(state.board, state.shapeAtCell, state.tray, state.available, state.score);
        return restored;
    }

    private void restoreColors() {
        Arrays.fill(cellColor, 0);
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                int index = row * Board.SIZE + col;
                if (!game.board().filled(row, col)) {
                    continue;
                }
                int shapeId = game.shapeAt(row, col);
                cellColor[index] = shapeId >= 0
                        ? PieceCatalog.byId(shapeId).color()
                        : RESTORED_COLOR;
            }
        }
    }

    public void save() {
        prefs.edit()
                .putString(KEY_STATE, GameSave.encode(game))
                .putInt(KEY_BEST, bestScore)
                .putBoolean(KEY_MUTED, muted)
                .apply();
    }

    public void newGame() {
        game = new Game(System.currentTimeMillis() / 1000L);
        Arrays.fill(cellColor, 0);
        Arrays.fill(popAt, 0L);
        flashes.clear();
        floats.clear();
        comboMultiplier = 0;
        comboAt = 0L;
        shownScore = 0f;
        gameOverProgress = 0f;
        newBestThisGame = false;
        dragSlot = -1;
        hoverRow = -1;
        hoverCol = -1;
        hoverValid = false;
        save();
        invalidate();
    }

    // --- geometry ------------------------------------------------------------

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        computeLayout(width, height);
    }

    private void computeLayout(int width, int height) {
        pad = Math.min(width, height) * 0.045f;
        headerHeight = Math.min(height * 0.13f, width * 0.24f);
        buttonHeight = Math.min(height * 0.062f, dp(46f));
        trayHeight = Math.min(height * 0.205f, width * 0.30f);

        float gapHeader = pad * 0.7f;
        float gapTray = pad * 0.55f;
        float gapButton = pad * 0.45f;

        float available = height - headerHeight - trayHeight - buttonHeight
                - 3f * pad - gapHeader - gapTray - gapButton;
        boardSize = Math.min(width - 2f * pad, available);
        boardSize = Math.max(boardSize, Math.min(width - 2f * pad, height * 0.34f));

        float stack = headerHeight + gapHeader + boardSize + gapTray + trayHeight + gapButton + buttonHeight;
        float top = Math.max(pad * 0.5f, (height - stack) * 0.42f);

        headerTop = top;
        boardLeft = (width - boardSize) / 2f;
        boardTop = top + headerHeight + gapHeader;
        boardRect.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
        cellSize = boardSize / Board.SIZE;
        dragLift = cellSize * 1.15f;

        trayTop = boardTop + boardSize + gapTray;
        trayRect.set(pad, trayTop, width - pad, trayTop + trayHeight);

        buttonTop = trayTop + trayHeight + gapButton;
        float buttonWidth = Math.min(width * 0.46f, dp(200f));
        newGameRect.set((width - buttonWidth) / 2f, buttonTop,
                (width + buttonWidth) / 2f, buttonTop + buttonHeight);

        float muteSize = buttonHeight;
        float muteCenterX = width - pad - muteSize / 2f;
        float muteCenterY = buttonTop + buttonHeight / 2f;
        muteRect.set(muteCenterX - muteSize / 2f, muteCenterY - muteSize / 2f,
                muteCenterX + muteSize / 2f, muteCenterY + muteSize / 2f);

        float inner = pad * 0.55f;
        float slotGap = pad * 0.45f;
        float slotWidth = (trayRect.width() - 2f * inner - 2f * slotGap) / 3f;
        for (int slot = 0; slot < Game.TRAY_SLOTS; slot++) {
            float left = trayRect.left + inner + slot * (slotWidth + slotGap);
            slotRects[slot].set(left, trayRect.top + inner, left + slotWidth, trayRect.bottom - inner);
        }

        backgroundShader = new LinearGradient(0f, 0f, 0f, height,
                BACKGROUND_TOP, BACKGROUND_BOTTOM, Shader.TileMode.CLAMP);

        // Pre-built sparkle halos, quantised by alpha, so the clear effect allocates nothing.
        sparkleGlows = new Shader[GLOW_STEPS + 1];
        for (int step = 0; step <= GLOW_STEPS; step++) {
            int core = 0x99 * step / GLOW_STEPS;
            int middle = 0x33 * step / GLOW_STEPS;
            sparkleGlows[step] = new RadialGradient(0f, 0f, cellSize * 0.62f,
                    new int[]{(core << 24) | 0xFFFFFF, (middle << 24) | 0xFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * scaledDensity;
    }

    // --- frame loop ----------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrameAt == 0L ? 16f : Math.min(50f, now - lastFrameAt);
        lastFrameAt = now;

        updateAnimations(now, dt);

        drawBackground(canvas);
        drawHeader(canvas);
        drawBoard(canvas, now);
        drawTray(canvas);
        drawNewGameButton(canvas);
        drawMuteButton(canvas);
        drawDraggedPiece(canvas);
        drawEffects(canvas, now);
        drawGameOver(canvas);

        if (needsFrame(now)) {
            postInvalidateOnAnimation();
        }
    }

    private void updateAnimations(long now, float dt) {
        int target = game.score();
        if (Math.abs(shownScore - target) > 0.5f) {
            shownScore += (target - shownScore) * Math.min(1f, dt / 90f);
        } else {
            shownScore = target;
        }

        boolean over = game.isGameOver();
        float step = dt / GAME_OVER_MS;
        gameOverProgress = over
                ? Math.min(1f, gameOverProgress + step)
                : Math.max(0f, gameOverProgress - step);

        for (int i = flashes.size() - 1; i >= 0; i--) {
            if (now - flashes.get(i).at > flashes.get(i).duration) {
                flashes.remove(i);
            }
        }
        for (int i = floats.size() - 1; i >= 0; i--) {
            if (now - floats.get(i).at > FLOAT_MS) {
                floats.remove(i);
            }
        }
    }

    private boolean needsFrame(long now) {
        if (Math.abs(shownScore - game.score()) > 0.5f) {
            return true;
        }
        if (gameOverProgress > 0f && gameOverProgress < 1f) {
            return true;
        }
        if (game.isGameOver() && gameOverProgress < 1f) {
            return true;
        }
        for (ClearFlash flash : flashes) {
            if (now - flash.at <= flash.duration) {
                return true;
            }
        }
        for (FloatText text : floats) {
            if (now - text.at <= FLOAT_MS) {
                return true;
            }
        }
        return comboMultiplier > 0 && now - comboAt <= COMBO_MS;
    }

    // --- drawing: background and header --------------------------------------

    private void drawBackground(Canvas canvas) {
        paint.setShader(backgroundShader);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        // Two soft cool glows to keep the flat gradient from looking dead.
        paint.setColor(0x140FA8BE);
        canvas.drawCircle(getWidth() * 0.15f, getHeight() * 0.05f, getWidth() * 0.45f, paint);
        paint.setColor(0x0F6FA8D8);
        canvas.drawCircle(getWidth() * 0.92f, getHeight() * 0.62f, getWidth() * 0.38f, paint);
    }

    private void drawHeader(Canvas canvas) {
        float centerX = getWidth() / 2f;

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTypeface(mediumFace);
        textPaint.setColor(TEXT_DIM);
        textPaint.setTextSize(sp(11f));
        textPaint.setLetterSpacing(0.24f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("SCORE", centerX, headerTop + headerHeight * 0.22f, textPaint);
        textPaint.setLetterSpacing(0f);

        String scoreText = formatScore(Math.round(shownScore));
        textPaint.setTypeface(blackFace);
        textPaint.setColor(TEXT_PRIMARY);
        float scoreSize = sp(40f);
        textPaint.setTextSize(scoreSize);
        float limit = getWidth() * 0.68f;
        while (textPaint.measureText(scoreText) > limit && scoreSize > sp(20f)) {
            scoreSize -= sp(1f);
            textPaint.setTextSize(scoreSize);
        }
        canvas.drawText(scoreText, centerX, headerTop + headerHeight * 0.90f, textPaint);

        drawBestScore(canvas);
    }

    /** Small best-score block in the top-right corner: a star, a label and the value. */
    private void drawBestScore(Canvas canvas) {
        float right = getWidth() - pad * 1.3f;
        float labelBaseline = headerTop + dp(12f);
        float valueBaseline = headerTop + dp(31f);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTypeface(mediumFace);
        textPaint.setTextSize(sp(9f));
        textPaint.setLetterSpacing(0.16f);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setColor(TEXT_DIM);
        canvas.drawText("BEST", right, labelBaseline, textPaint);
        textPaint.setLetterSpacing(0f);

        float labelWidth = textPaint.measureText("BEST");
        float starRadius = dp(4.4f);
        drawStar(canvas, right - labelWidth - dp(9f), labelBaseline - dp(3f), starRadius, ACCENT);

        textPaint.setTypeface(blackFace);
        textPaint.setTextSize(sp(17f));
        textPaint.setColor(TEXT_PRIMARY);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatScore(bestScore), right, valueBaseline, textPaint);
    }

    private void drawStar(Canvas canvas, float centerX, float centerY, float radius, int color) {
        starPath.reset();
        for (int point = 0; point < 10; point++) {
            double angle = Math.PI / 2.0 + point * Math.PI / 5.0;
            float pointRadius = point % 2 == 0 ? radius : radius * 0.45f;
            float x = (float) (centerX + Math.cos(angle) * pointRadius);
            float y = (float) (centerY - Math.sin(angle) * pointRadius);
            if (point == 0) {
                starPath.moveTo(x, y);
            } else {
                starPath.lineTo(x, y);
            }
        }
        starPath.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        canvas.drawPath(starPath, paint);
    }

    private String formatScore(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    // --- drawing: board ------------------------------------------------------

    private void drawBoard(Canvas canvas, long now) {
        float radius = cellSize * 0.26f;

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0x26243B4A);
        scratch.set(boardRect);
        scratch.offset(0f, dp(4f));
        canvas.drawRoundRect(scratch, radius, radius, paint);

        paint.setColor(BOARD_PANEL);
        canvas.drawRoundRect(boardRect, radius, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(0x33243B4A);
        scratch.set(boardRect);
        scratch.inset(dp(0.5f), dp(0.5f));
        canvas.drawRoundRect(scratch, radius, radius, paint);

        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                cellRect(row, col, scratch);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(EMPTY_CELL);
                canvas.drawRoundRect(scratch, cellSize * 0.09f, cellSize * 0.09f, paint);
            }
        }

        if (dragSlot >= 0 && hoverValid) {
            drawPlacementPreview(canvas);
        }

        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                int index = row * Board.SIZE + col;
                if (!game.board().filled(row, col)) {
                    continue;
                }
                int color = cellColor[index] != 0 ? cellColor[index] : RESTORED_COLOR;
                cellRect(row, col, scratch);

                float scale = 1f;
                long age = now - popAt[index];
                if (popAt[index] != 0L && age >= 0 && age < POP_MS) {
                    float t = age / (float) POP_MS;
                    scale = 0.80f + 0.20f * easeOut(t);
                }
                drawBlock(canvas, scratch, color, 1f, scale);
            }
        }
    }

    private void drawPlacementPreview(Canvas canvas) {
        Piece piece = game.trayPiece(dragSlot);
        if (piece == null) {
            return;
        }
        int color = piece.color();

        // Rows and columns that this drop would clear get a soft wash.
        int[] rowFill = new int[Board.SIZE];
        int[] colFill = new int[Board.SIZE];
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (game.board().filled(row, col)) {
                    rowFill[row]++;
                    colFill[col]++;
                }
            }
        }
        for (int r = 0; r < Piece.GRID; r++) {
            for (int c = 0; c < Piece.GRID; c++) {
                if (piece.cellAt(r, c)) {
                    int boardRow = hoverRow + r;
                    int boardCol = hoverCol + c;
                    if (boardRow >= 0 && boardRow < Board.SIZE && boardCol >= 0 && boardCol < Board.SIZE) {
                        rowFill[boardRow]++;
                        colFill[boardCol]++;
                    }
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x1F0F6E7D);
        for (int row = 0; row < Board.SIZE; row++) {
            if (rowFill[row] >= Board.SIZE) {
                scratch.set(boardLeft, boardTop + row * cellSize,
                        boardLeft + boardSize, boardTop + (row + 1) * cellSize);
                canvas.drawRect(scratch, paint);
            }
        }
        for (int col = 0; col < Board.SIZE; col++) {
            if (colFill[col] >= Board.SIZE) {
                scratch.set(boardLeft + col * cellSize, boardTop,
                        boardLeft + (col + 1) * cellSize, boardTop + boardSize);
                canvas.drawRect(scratch, paint);
            }
        }

        for (int r = 0; r < Piece.GRID; r++) {
            for (int c = 0; c < Piece.GRID; c++) {
                if (!piece.cellAt(r, c)) {
                    continue;
                }
                cellRect(hoverRow + r, hoverCol + c, scratch);
                drawBlock(canvas, scratch, color, 0.42f, 1f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.2f));
                paint.setColor(0xCC1C2B36);
                canvas.drawRoundRect(scratch, cellSize * 0.12f, cellSize * 0.12f, paint);
            }
        }
    }

    private void cellRect(int row, int col, RectF out) {
        float gap = cellSize * 0.016f;
        out.set(boardLeft + col * cellSize + gap,
                boardTop + row * cellSize + gap,
                boardLeft + (col + 1) * cellSize - gap,
                boardTop + (row + 1) * cellSize - gap);
    }


    private void drawBlock(Canvas canvas, RectF source, int color, float alpha, float scale) {
        float halfWidth = source.width() * 0.5f * scale;
        float halfHeight = source.height() * 0.5f * scale;
        float centerX = source.centerX();
        float centerY = source.centerY();
        blockRect.set(centerX - halfWidth, centerY - halfHeight,
                centerX + halfWidth, centerY + halfHeight);

        int alphaByte = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
        float shortSide = Math.min(halfWidth, halfHeight);
        float radius = shortSide * 0.22f;
        float bevel = Math.max(1.5f, shortSide * 0.22f);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);

        // The dark base doubles as the shadowed outline around the whole tile.
        paint.setColor(darken(color, 0.52f));
        paint.setAlpha(alphaByte);
        canvas.drawRoundRect(blockRect, radius, radius, paint);

        float left = blockRect.left;
        float top = blockRect.top;
        float right = blockRect.right;
        float bottom = blockRect.bottom;

        topBevel.reset();
        topBevel.moveTo(left, top);
        topBevel.lineTo(right, top);
        topBevel.lineTo(right - bevel, top + bevel);
        topBevel.lineTo(left + bevel, top + bevel);
        topBevel.close();

        leftBevel.reset();
        leftBevel.moveTo(left, top);
        leftBevel.lineTo(left + bevel, top + bevel);
        leftBevel.lineTo(left + bevel, bottom - bevel);
        leftBevel.lineTo(left, bottom);
        leftBevel.close();

        bottomBevel.reset();
        bottomBevel.moveTo(left, bottom);
        bottomBevel.lineTo(left + bevel, bottom - bevel);
        bottomBevel.lineTo(right - bevel, bottom - bevel);
        bottomBevel.lineTo(right, bottom);
        bottomBevel.close();

        rightBevel.reset();
        rightBevel.moveTo(right, top);
        rightBevel.lineTo(right, bottom);
        rightBevel.lineTo(right - bevel, bottom - bevel);
        rightBevel.lineTo(right - bevel, top + bevel);
        rightBevel.close();

        clipPath.reset();
        clipPath.addRoundRect(blockRect, radius, radius, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(clipPath);

        paint.setColor(lighten(color, 0.70f));
        paint.setAlpha(alphaByte);
        canvas.drawPath(topBevel, paint);

        paint.setColor(lighten(color, 0.30f));
        canvas.drawPath(leftBevel, paint);

        paint.setColor(darken(color, 0.26f));
        canvas.drawPath(bottomBevel, paint);
        canvas.drawPath(rightBevel, paint);

        faceRect.set(left + bevel, top + bevel, right - bevel, bottom - bevel);
        float faceRadius = Math.max(1f, radius - bevel * 0.5f);
        paint.setColor(color);
        paint.setAlpha(alphaByte);
        canvas.drawRoundRect(faceRect, faceRadius, faceRadius, paint);

        // Thin specular line just under the top chamfer.
        glossRect.set(faceRect.left + bevel * 0.25f, faceRect.top + bevel * 0.18f,
                faceRect.right - bevel * 0.25f, faceRect.top + bevel * 0.72f);
        paint.setColor(lighten(color, 0.80f));
        paint.setAlpha((int) (alphaByte * 0.26f));
        canvas.drawRoundRect(glossRect, glossRect.height() / 2f, glossRect.height() / 2f, paint);

        canvas.restoreToCount(saved);
        paint.setAlpha(255);
    }

    // --- drawing: tray and buttons ------------------------------------------

    private void drawTray(Canvas canvas) {
        float radius = cellSize * 0.24f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(TRAY_PANEL);
        canvas.drawRoundRect(trayRect, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(0x1F243B4A);
        scratch.set(trayRect);
        scratch.inset(dp(0.5f), dp(0.5f));
        canvas.drawRoundRect(scratch, radius, radius, paint);

        for (int slot = 0; slot < Game.TRAY_SLOTS; slot++) {
            RectF bounds = slotRects[slot];
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(SLOT_PANEL);
            canvas.drawRoundRect(bounds, cellSize * 0.18f, cellSize * 0.18f, paint);

            if (!game.isAvailable(slot) || dragSlot == slot) {
                continue;
            }
            Piece piece = game.trayPiece(slot);
            if (piece == null) {
                continue;
            }
            float availableWidth = bounds.width() * 0.78f;
            float availableHeight = bounds.height() * 0.74f;
            float pitch = Math.min(availableWidth / Math.max(1, piece.width()),
                    availableHeight / Math.max(1, piece.height()));
            pitch = Math.min(pitch, cellSize * 0.60f);
            float totalWidth = piece.width() * pitch;
            float totalHeight = piece.height() * pitch;
            float left = bounds.centerX() - totalWidth / 2f;
            float top = bounds.centerY() - totalHeight / 2f;
            drawPiece(canvas, piece, left, top, pitch, 1f);
        }
    }

    private void drawPiece(Canvas canvas, Piece piece, float left, float top, float pitch, float alpha) {
        float gap = pitch * 0.018f;
        for (int r = 0; r < Piece.GRID; r++) {
            for (int c = 0; c < Piece.GRID; c++) {
                if (!piece.cellAt(r, c)) {
                    continue;
                }
                scratch.set(left + c * pitch + gap, top + r * pitch + gap,
                        left + (c + 1) * pitch - gap, top + (r + 1) * pitch - gap);
                drawBlock(canvas, scratch, piece.color(), alpha, 1f);
            }
        }
    }

    private void drawDraggedPiece(Canvas canvas) {
        if (dragSlot < 0) {
            return;
        }
        Piece piece = game.trayPiece(dragSlot);
        if (piece == null) {
            return;
        }
        float pitch = cellSize;
        float totalWidth = piece.width() * pitch;
        float totalHeight = piece.height() * pitch;
        float left = dragX - totalWidth / 2f;
        float top = dragY - dragLift - totalHeight / 2f;
        drawPiece(canvas, piece, left, top, pitch, 0.96f);
    }

    private void drawNewGameButton(Canvas canvas) {
        float radius = newGameRect.height() / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE7F3F6);
        canvas.drawRoundRect(newGameRect, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(0x550F6E7D);
        canvas.drawRoundRect(newGameRect, radius, radius, paint);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTypeface(mediumFace);
        textPaint.setTextSize(sp(12f));
        textPaint.setLetterSpacing(0.16f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFF0E5A66);
        canvas.drawText("NEW GAME", newGameRect.centerX(),
                centerBaseline(newGameRect.centerY()), textPaint);
        textPaint.setLetterSpacing(0f);
    }

    private void drawMuteButton(Canvas canvas) {
        float centerX = muteRect.centerX();
        float centerY = muteRect.centerY();
        float radius = muteRect.height() / 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0xFFE7F3F6);
        canvas.drawCircle(centerX, centerY, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(muted ? 0x990F6E7D : 0x440F6E7D);
        canvas.drawCircle(centerX, centerY, radius, paint);

        drawSpeakerIcon(canvas);
    }

    private void drawSpeakerIcon(Canvas canvas) {
        float centerX = muteRect.centerX();
        float centerY = muteRect.centerY();
        float size = muteRect.width() * 0.52f;

        speakerPath.reset();
        speakerPath.moveTo(centerX - size * 0.42f, centerY - size * 0.16f);
        speakerPath.lineTo(centerX - size * 0.12f, centerY - size * 0.16f);
        speakerPath.lineTo(centerX + size * 0.16f, centerY - size * 0.44f);
        speakerPath.lineTo(centerX + size * 0.16f, centerY + size * 0.44f);
        speakerPath.lineTo(centerX - size * 0.12f, centerY + size * 0.16f);
        speakerPath.lineTo(centerX - size * 0.42f, centerY + size * 0.16f);
        speakerPath.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(muted ? 0xFFA3B7C2 : 0xFF2A4352);
        canvas.drawPath(speakerPath, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (muted) {
            paint.setStrokeWidth(dp(2f));
            paint.setColor(0xFF0F6E7D);
            canvas.drawLine(centerX + size * 0.16f, centerY - size * 0.30f,
                    centerX + size * 0.66f, centerY + size * 0.30f, paint);
            canvas.drawLine(centerX + size * 0.66f, centerY - size * 0.30f,
                    centerX + size * 0.16f, centerY + size * 0.30f, paint);
        } else {
            paint.setStrokeWidth(dp(1.8f));
            paint.setColor(0xFF2A4352);
            for (int ring = 1; ring <= 2; ring++) {
                float ringRadius = size * (0.24f + 0.24f * ring);
                arcRect.set(centerX + size * 0.16f - ringRadius, centerY - ringRadius,
                        centerX + size * 0.16f + ringRadius, centerY + ringRadius);
                canvas.drawArc(arcRect, -52f, 104f, false, paint);
            }
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void toggleMute() {
        muted = !muted;
        sound.setMuted(muted);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (!muted) {
            // A short blip confirms that sound is back on.
            sound.playPlace();
        }
        prefs.edit().putBoolean(KEY_MUTED, muted).apply();
        invalidate();
    }

    // --- drawing: effects and game over -------------------------------------

    private void drawEffects(Canvas canvas, long now) {
        for (ClearFlash flash : flashes) {
            long age = now - flash.at;
            if (age < 0 || age > flash.duration) {
                continue;
            }
            for (int index = 0; index < Board.CELLS; index++) {
                if (!flash.cells[index]) {
                    continue;
                }
                long local = age - flash.delay[index];
                cellRect(index / Board.SIZE, index % Board.SIZE, scratch);
                float centerX = scratch.centerX();
                float centerY = scratch.centerY();

                float t = Math.max(0f, Math.min(1f, local / (float) SLIDE_MS));
                if (t < 1f) {
                    // The tile slides a full cell to the right, turning translucent as it
                    // goes, and is clipped to the board so edge tiles never leave it.
                    scratch.offset(t * cellSize, 0f);
                    int savedClip = canvas.save();
                    canvas.clipRect(boardRect);
                    drawBlock(canvas, scratch, flash.colors[index],
                            1f - (float) Math.pow(t, 1.5), 1f);
                    canvas.restoreToCount(savedClip);
                }

                float spark = (local - SPARK_DELAY_MS) / (float) SPARK_MS;
                if (spark > 0f && spark < 1f) {
                    drawSparkle(canvas, centerX, centerY, cellSize * 0.48f, spark);
                }
            }
        }

        for (FloatText text : floats) {
            long age = now - text.at;
            if (age < 0 || age > FLOAT_MS) {
                continue;
            }
            float t = age / (float) FLOAT_MS;
            float alpha = 1f - t;
            textPaint.setStyle(Paint.Style.FILL);
            textPaint.setTypeface(blackFace);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(text.size);
            textPaint.setColor(text.color);
            textPaint.setAlpha((int) (alpha * 255f));
            textPaint.setShadowLayer(dp(4f), 0f, dp(2f), 0x55000000);
            canvas.drawText(text.text, text.x, text.y - dp(26f) * easeOut(t), textPaint);
            textPaint.clearShadowLayer();
        }

        if (comboMultiplier > 1) {
            long age = now - comboAt;
            if (age >= 0 && age <= COMBO_MS) {
                float t = age / (float) COMBO_MS;
                float pop = 1f + 0.30f * (1f - easeOut(Math.min(1f, t * 3.2f)));
                float alpha = t < 0.68f ? 1f : 1f - (t - 0.68f) / 0.32f;
                textPaint.setStyle(Paint.Style.FILL);
                textPaint.setTypeface(blackFace);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTextSize(sp(30f) * pop);
                textPaint.setColor(ACCENT);
                textPaint.setAlpha((int) (Math.max(0f, alpha) * 255f));
                textPaint.setShadowLayer(dp(8f), 0f, dp(3f), 0x66000000);
                canvas.drawText("COMBO x" + comboMultiplier,
                        getWidth() / 2f,
                        boardTop + boardSize * 0.46f, textPaint);
                textPaint.clearShadowLayer();
            }
        }
    }

    private void drawGameOver(Canvas canvas) {
        if (gameOverProgress <= 0.001f) {
            return;
        }
        float t = easeOut(gameOverProgress);
        int width = getWidth();
        int height = getHeight();

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0xFF0B1B24);
        paint.setAlpha((int) (205f * t));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setAlpha(255);

        float panelWidth = Math.min(width * 0.84f, dp(340f));
        float panelHeight = Math.min(height * 0.44f, dp(350f));
        float scale = 0.94f + 0.06f * t;
        float halfWidth = panelWidth * 0.5f * scale;
        float halfHeight = panelHeight * 0.5f * scale;
        float centerX = width / 2f;
        float centerY = height / 2f;

        scratch.set(centerX - halfWidth, centerY - halfHeight,
                centerX + halfWidth, centerY + halfHeight);
        float radius = Math.min(halfWidth, halfHeight) * 0.20f;
        paint.setColor(0xFFFFFFFF);
        canvas.drawRoundRect(scratch, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(0x660F6E7D);
        canvas.drawRoundRect(scratch, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);

        float left = scratch.left;
        float top = scratch.top;
        float panelW = scratch.width();
        float panelH = scratch.height();

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTypeface(blackFace);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(21f));
        textPaint.setColor(TEXT_PRIMARY);
        canvas.drawText("NO MORE MOVES", centerX, top + panelH * 0.17f, textPaint);

        textPaint.setTypeface(mediumFace);
        textPaint.setTextSize(sp(11f));
        textPaint.setLetterSpacing(0.18f);
        textPaint.setColor(TEXT_DIM);
        canvas.drawText("SCORE", centerX, top + panelH * 0.33f, textPaint);
        textPaint.setLetterSpacing(0f);

        textPaint.setTypeface(blackFace);
        textPaint.setTextSize(sp(34f));
        textPaint.setColor(ACCENT);
        canvas.drawText(formatScore(game.score()), centerX, top + panelH * 0.49f, textPaint);

        textPaint.setTypeface(boldFace);
        textPaint.setTextSize(sp(13f));
        textPaint.setColor(TEXT_DIM);
        String bestLine = newBestThisGame
                ? "BEST " + formatScore(bestScore) + "  \u2022  NEW BEST!"
                : "BEST " + formatScore(bestScore);
        canvas.drawText(bestLine, centerX, top + panelH * 0.60f, textPaint);

        float buttonWidth = panelW * 0.70f;
        float buttonH = Math.min(dp(50f), panelH * 0.18f);
        float buttonLeft = centerX - buttonWidth / 2f;
        float buttonTop = top + panelH * 0.72f;
        playAgainRect.set(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonH);

        paint.setColor(ACCENT);
        canvas.drawRoundRect(playAgainRect, buttonH / 2f, buttonH / 2f, paint);

        textPaint.setTypeface(blackFace);
        textPaint.setTextSize(sp(14f));
        textPaint.setLetterSpacing(0.08f);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText("PLAY AGAIN", playAgainRect.centerX(),
                centerBaseline(playAgainRect.centerY()), textPaint);
        textPaint.setLetterSpacing(0f);
        textPaint.setTextSize(sp(10f));
        textPaint.setColor(TEXT_DIM);
        canvas.drawText("tap to restart", centerX, top + panelH * 0.955f, textPaint);
    }

    private float centerBaseline(float centerY) {
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        return centerY - (metrics.ascent + metrics.descent) / 2f;
    }

    // --- input ---------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                return handleDown(event.getX(), event.getY());
            case MotionEvent.ACTION_MOVE: {
                int index = event.findPointerIndex(activePointerId);
                if (index < 0) {
                    return true;
                }
                if (dragSlot >= 0) {
                    dragX = event.getX(index);
                    dragY = event.getY(index);
                    updateHover();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                cancelDrag();
                return true;
            case MotionEvent.ACTION_UP: {
                int index = event.findPointerIndex(activePointerId);
                if (index >= 0 && dragSlot >= 0) {
                    dragX = event.getX(index);
                    dragY = event.getY(index);
                    updateHover();
                }
                if (dragSlot >= 0) {
                    commitPlacement();
                }
                cancelDrag();
                activePointerId = -1;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelDrag();
                activePointerId = -1;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sound.release();
    }

    private boolean handleDown(float x, float y) {
        if (game.isGameOver()) {
            if (gameOverProgress > 0.5f && playAgainRect.contains(x, y)) {
                newGame();
            }
            return true;
        }
        if (newGameRect.contains(x, y)) {
            newGame();
            return true;
        }
        if (muteRect.contains(x, y)) {
            toggleMute();
            return true;
        }
        for (int slot = 0; slot < Game.TRAY_SLOTS; slot++) {
            if (game.isAvailable(slot) && expandedContains(slotRects[slot], x, y)) {
                dragSlot = slot;
                dragX = x;
                dragY = y;
                hoverRow = -1;
                hoverCol = -1;
                hoverValid = false;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                return true;
            }
        }
        return true;
    }

    private boolean expandedContains(RectF bounds, float x, float y) {
        float margin = cellSize * 0.18f;
        return x >= bounds.left - margin && x <= bounds.right + margin
                && y >= bounds.top - margin && y <= bounds.bottom + margin;
    }

    private void cancelDrag() {
        dragSlot = -1;
        hoverRow = -1;
        hoverCol = -1;
        hoverValid = false;
    }

    private void updateHover() {
        hoverValid = false;
        hoverRow = -1;
        hoverCol = -1;
        if (dragSlot < 0) {
            return;
        }
        Piece piece = game.trayPiece(dragSlot);
        if (piece == null) {
            return;
        }
        float totalWidth = piece.width() * cellSize;
        float totalHeight = piece.height() * cellSize;
        float left = dragX - totalWidth / 2f;
        float top = dragY - dragLift - totalHeight / 2f;

        int col = Math.round((left - boardLeft) / cellSize);
        int row = Math.round((top - boardTop) / cellSize);
        if (row < -1 || row > Board.SIZE || col < -1 || col > Board.SIZE) {
            return;
        }
        row = clamp(row, 0, Board.SIZE - 1);
        col = clamp(col, 0, Board.SIZE - 1);
        if (game.canPlace(dragSlot, row, col)) {
            hoverRow = row;
            hoverCol = col;
            hoverValid = true;
        }
    }

    private void commitPlacement() {
        if (!hoverValid || dragSlot < 0) {
            return;
        }
        int slot = dragSlot;
        Piece piece = game.trayPiece(slot);
        if (piece == null) {
            return;
        }
        int row = hoverRow;
        int col = hoverCol;
        int color = piece.color();
        long now = SystemClock.uptimeMillis();

        PlacementResult result = game.place(slot, row, col);
        if (!result.placed) {
            return;
        }

        for (int r = 0; r < Piece.GRID; r++) {
            for (int c = 0; c < Piece.GRID; c++) {
                if (!piece.cellAt(r, c)) {
                    continue;
                }
                int boardRow = row + r;
                int boardCol = col + c;
                if (!game.board().inside(boardRow, boardCol)) {
                    continue;
                }
                int index = boardRow * Board.SIZE + boardCol;
                cellColor[index] = color;
                popAt[index] = now;
            }
        }

        ClearFlash flash = new ClearFlash(now);
        boolean anyCleared = false;
        for (int clearedRow : result.rows) {
            for (int c = 0; c < Board.SIZE; c++) {
                anyCleared |= captureCleared(flash, clearedRow, c);
            }
        }
        for (int clearedCol : result.columns) {
            for (int r = 0; r < Board.SIZE; r++) {
                anyCleared |= captureCleared(flash, r, clearedCol);
            }
        }
        if (anyCleared) {
            long sequence = 0L;
            for (int r = Board.SIZE - 1; r >= 0; r--) {
                for (int c = 0; c < Board.SIZE; c++) {
                    int index = r * Board.SIZE + c;
                    if (flash.cells[index]) {
                        flash.delay[index] = sequence * CLEAR_STEP_MS;
                        sequence++;
                    }
                }
            }
            flash.duration = Math.max(0L, sequence - 1L) * CLEAR_STEP_MS
                    + SPARK_DELAY_MS + SPARK_MS + 20L;
            flashes.add(flash);
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } else {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        float centerX = boardLeft + (col + piece.width() / 2f) * cellSize;
        float centerY = boardTop + (row + piece.height() / 2f) * cellSize;
        floats.add(new FloatText(now, "+" + result.gained, centerX, centerY,
                result.multiplier > 1 ? ACCENT : TEXT_PRIMARY, sp(23f)));

        if (result.multiplier > 1) {
            comboAt = now;
            comboMultiplier = result.multiplier;
        }

        if (game.score() > bestScore) {
            bestScore = game.score();
            newBestThisGame = true;
        }

        sound.playPlace();
        if (result.linesCleared > 0) {
            postDelayed(sound::playClear, 90L);
            if (result.multiplier > 1) {
                postDelayed(sound::playCombo, 230L);
            }
        }
        if (game.isGameOver()) {
            postDelayed(sound::playGameOver, 420L);
        }
        save();
    }

    private void drawSparkle(Canvas canvas, float centerX, float centerY, float radius, float t) {
        float grow = t < 0.30f
                ? easeOut(t / 0.30f)
                : 1f - 0.45f * ((t - 0.30f) / 0.70f);
        float alpha = t < 0.30f ? t / 0.30f : 1f - 0.9f * ((t - 0.30f) / 0.70f);
        if (t > 0.92f) {
            alpha *= (1f - t) / 0.08f;
        }
        if (grow <= 0f || alpha <= 0f) {
            return;
        }
        float r = radius * grow;

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(255);

        int glowStep = (int) (alpha * GLOW_STEPS + 0.5f);
        if (sparkleGlows != null && glowStep > 0) {
            float glowScale = r / (cellSize * 0.62f);
            paint.setShader(sparkleGlows[Math.min(GLOW_STEPS, glowStep)]);
            int savedGlow = canvas.save();
            canvas.translate(centerX, centerY);
            canvas.scale(glowScale, glowScale);
            canvas.drawCircle(0f, 0f, cellSize * 0.62f, paint);
            canvas.restoreToCount(savedGlow);
            paint.setShader(null);
        }

        // The whole star turns slowly as it blooms and fades.
        float spin = -14f + 36f * t;
        int spinSaved = canvas.save();
        canvas.rotate(spin, centerX, centerY);
        buildSparkle(centerX, centerY, r);
        paint.setAlpha((int) (alpha * 235f));
        canvas.drawPath(sparklePath, paint);

        int saved = canvas.save();
        canvas.rotate(45f, centerX, centerY);
        buildSparkle(centerX, centerY, r * 0.44f);
        paint.setAlpha((int) (alpha * 95f));
        canvas.drawPath(sparklePath, paint);
        canvas.restoreToCount(saved);
        canvas.restoreToCount(spinSaved);

        paint.setAlpha((int) (alpha * 255f));
        canvas.drawCircle(centerX, centerY, Math.max(dp(1f), r * 0.08f), paint);
        paint.setAlpha(255);
    }

    private void buildSparkle(float centerX, float centerY, float radius) {
        float waist = radius * 0.14f;
        sparklePath.reset();
        sparklePath.moveTo(centerX, centerY - radius);
        sparklePath.lineTo(centerX + waist, centerY - waist);
        sparklePath.lineTo(centerX + radius, centerY);
        sparklePath.lineTo(centerX + waist, centerY + waist);
        sparklePath.lineTo(centerX, centerY + radius);
        sparklePath.lineTo(centerX - waist, centerY + waist);
        sparklePath.lineTo(centerX - radius, centerY);
        sparklePath.lineTo(centerX - waist, centerY - waist);
        sparklePath.close();
    }

    private boolean captureCleared(ClearFlash flash, int row, int col) {
        int index = row * Board.SIZE + col;
        if (cellColor[index] == 0) {
            return false;
        }
        flash.cells[index] = true;
        flash.colors[index] = cellColor[index];
        cellColor[index] = 0;
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float easeOut(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        float inverse = 1f - clamped;
        return 1f - inverse * inverse * inverse;
    }

    private static int lighten(int color, float amount) {
        int r = (int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * amount);
        int g = (int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * amount);
        int b = (int) ((color & 0xFF) + (255 - (color & 0xFF)) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, float amount) {
        int r = (int) (((color >> 16) & 0xFF) * (1f - amount));
        int g = (int) (((color >> 8) & 0xFF) * (1f - amount));
        int b = (int) ((color & 0xFF) * (1f - amount));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
