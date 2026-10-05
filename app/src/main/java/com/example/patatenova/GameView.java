package com.example.patatenova;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

public class GameView extends View {

    /* ÉCRANS */
    enum GameState {
        START,
        PLAYING,
        PAUSED,
        GAME_OVER
    }
    private GameState gameState = GameState.START;

    /* VAISSEAU */
    private float shipX, shipY, shipSize;
    private final Path shipPath = new Path();

    /* ÉTOILES */
    private static final int STAR_COUNT = 40;
    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starDepth = new float[STAR_COUNT];   // 0.3 à 1 : plus c'est grand, plus c'est rapide
    private float starSpeed;
    private final Random rnd = new Random();

    /* BOUTONS */
    private final RectF btn1 = new RectF();       // bouton principal des menus
    private final RectF btn2 = new RectF();
    private final RectF btn3 = new RectF();
    private final RectF btnPause = new RectF();   // bouton PAUSE en jeu
    private final RectF btnEnd = new RectF();     // bouton de test pour finir la partie

    // ---------------- Peintures (créées une seule fois) ----------------
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GameView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(8, 10, 24));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /* BOUCLE DE JEU */
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            update();
            invalidate();
            postDelayed(this, 16);
        }
    };

    public void startGame() {
        removeCallbacks(gameLoop);
        post(gameLoop);
    }

    public void stopGame() {
        removeCallbacks(gameLoop);
    }
    
    public void onActivityPause() {
        if (gameState == GameState.PLAYING) gameState = GameState.PAUSED;
        stopGame();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopGame();
    }

    /* DIMENSIONS DES OBJETS */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // vaisseau
        shipX = w / 2f;
        shipY = h * 0.68f;
        shipSize = w * 0.07f;
        shipPath.reset();
        shipPath.moveTo(0, -shipSize);
        shipPath.lineTo(shipSize * 0.8f, shipSize);
        shipPath.lineTo(0, shipSize * 0.5f);
        shipPath.lineTo(-shipSize * 0.8f, shipSize);
        shipPath.close();

        // étoiles
        starSpeed = h * 0.004f;
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = rnd.nextFloat() * w;
            starY[i] = rnd.nextFloat() * h;
            starDepth[i] = 0.3f + rnd.nextFloat() * 0.7f;
        }

        // boutons
        btn1.set(w * 0.2f, h * 0.55f, w * 0.8f, h * 0.62f);
        btn2.set(w * 0.2f, h * 0.66f, w * 0.8f, h * 0.73f);
        btn3.set(w * 0.2f, h * 0.77f, w * 0.8f, h * 0.84f);
        btnPause.set(w * 0.74f, h * 0.03f, w * 0.96f, h * 0.085f);
        btnEnd.set(w * 0.10f, h * 0.86f, w * 0.90f, h * 0.94f);

        textPaint.setTextSize(w * 0.05f);
    }

    /* MISE À JOUR*/
    private void update() {
        // Les étoiles ne défilent que pendant la partie
        if (gameState != GameState.PLAYING) return;

        for (int i = 0; i < STAR_COUNT; i++) {
            starY[i] += starSpeed * starDepth[i];
            if (starY[i] > getHeight()) {       // sortie en bas -> réapparition en haut
                starY[i] = 0;
                starX[i] = rnd.nextFloat() * getWidth();
            }
        }
    }

    /* DESSINS DES OBJETS */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawStars(canvas);   // le fond d'étoiles est commun à tous les écrans

        switch (gameState) {
            case START:
                drawStartScreen(canvas);
                break;
            case PLAYING:
                drawGame(canvas);
                break;
            case PAUSED:
                drawGame(canvas);          // la partie reste visible derrière
                drawPauseScreen(canvas);
                break;
            case GAME_OVER:
                drawGameOverScreen(canvas);
                break;
        }
    }

    private void drawStars(Canvas canvas) {
        fillPaint.setColor(Color.WHITE);
        for (int i = 0; i < STAR_COUNT; i++) {
            fillPaint.setAlpha((int) (80 + 175 * starDepth[i]));
            canvas.drawCircle(starX[i], starY[i], 1.5f + 2.5f * starDepth[i], fillPaint);
        }
        fillPaint.setAlpha(255);
    }

    private void drawShip(Canvas canvas) {
        canvas.save();
        canvas.translate(shipX, shipY);
        fillPaint.setColor(Color.rgb(0, 200, 220));
        canvas.drawPath(shipPath, fillPaint);
        canvas.restore();
    }

    private void drawButton(Canvas canvas, RectF r, String label, int color) {
        fillPaint.setColor(color);
        canvas.drawRoundRect(r, 24, 24, fillPaint);
        textPaint.setColor(Color.WHITE);
        float ty = r.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(label, r.centerX(), ty, textPaint);
    }

    private void drawTitle(Canvas canvas, String text, float y) {
        textPaint.setTextSize(getWidth() * 0.09f);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(text, getWidth() / 2f, y, textPaint);
        textPaint.setTextSize(getWidth() * 0.05f);
    }

    /* Écran Accueil */
    private void drawStartScreen(Canvas canvas) {
        drawTitle(canvas, "SPACE ESCAPE", getHeight() * 0.28f);
        drawButton(canvas, btn1, "JOUER", Color.rgb(40, 140, 70));
        // TODO plus tard : pseudo, options
    }

    /* Écran Partie */
    private void drawGame(Canvas canvas) {
        drawShip(canvas);
        drawButton(canvas, btnPause, "PAUSE", Color.argb(180, 80, 80, 100));
        drawButton(canvas, btnEnd, "TERMINER (test)", Color.rgb(170, 50, 50));
        // TODO plus tard : bouton Avancer, obstacles, barre de progression...
    }

    /* Écran Pause */
    private void drawPauseScreen(Canvas canvas) {
        fillPaint.setColor(Color.argb(190, 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
        drawTitle(canvas, "PAUSE", getHeight() * 0.40f);
        drawButton(canvas, btn1, "Reprendre", Color.rgb(40, 140, 70));
        drawButton(canvas, btn2, "Recommencer", Color.rgb(40, 90, 200));
        drawButton(canvas, btn3, "Abandonner (accueil)", Color.rgb(170, 50, 50));
    }

    /* Écran Fin Partie */
    private void drawGameOverScreen(Canvas canvas) {
        drawTitle(canvas, "FIN DE PARTIE", getHeight() * 0.28f);
        // TODO plus tard : score, obstacles évités...
        drawButton(canvas, btn1, "Rejouer", Color.rgb(40, 140, 70));
        drawButton(canvas, btn2, "Accueil", Color.rgb(40, 90, 200));
    }

    /* NAVIGATION ENTRE LES ÉCRANS */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;

        float tx = event.getX();
        float ty = event.getY();

        switch (gameState) {
            case START:
                if (btn1.contains(tx, ty)) gameState = GameState.PLAYING;
                break;

            case PLAYING:
                if (btnPause.contains(tx, ty)) gameState = GameState.PAUSED;
                else if (btnEnd.contains(tx, ty)) gameState = GameState.GAME_OVER;
                break;

            case PAUSED:
                if (btn1.contains(tx, ty)) gameState = GameState.PLAYING;        // reprendre
                else if (btn2.contains(tx, ty)) gameState = GameState.PLAYING;   // recommencer (rien à réinitialiser pour l'instant)
                else if (btn3.contains(tx, ty)) gameState = GameState.START;     // abandonner
                break;

            case GAME_OVER:
                if (btn1.contains(tx, ty)) gameState = GameState.PLAYING;        // rejouer
                else if (btn2.contains(tx, ty)) gameState = GameState.START;     // accueil
                break;
        }
        return true;
    }
}