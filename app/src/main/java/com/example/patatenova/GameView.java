package com.example.patatenova;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
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

    /* [PSEUDO] COMMUNICATION AVEC L'ACTIVITÉ */
    public interface Listener {
        void onStateChanged(GameState newState);   // l'écran a changé (afficher/cacher le champ)
        void onPlayRequested();                    // appui sur JOUER (l'activité valide le pseudo)
        void onBackgroundTapped();                 // appui ailleurs sur l'accueil (fermer le clavier)
    }
    private Listener listener;

    /* [PSEUDO] */
    private String playerName = Settings.DEFAULT_NAME;
    private final RectF nameFieldRect = new RectF();   // emplacement du champ de saisie (accueil)

    /* OBSTACLES */
    enum ObstacleType { ASTEROID, BLACK_HOLE }

    private static class Obstacle {
        ObstacleType type;
        float pos;          // position le long du parcours (comme distance)
        float offsetX;      // décalage visuel gauche/droite
        boolean resolved;   // true une fois que l'obstacle a touché le vaisseau
    }

    private final List<Obstacle> obstacles = new ArrayList<>();
    private int hitCount;   // compteur d'impacts : conservé mais non affiché pour l'instant
    private int hitFlash;

    private static final int OBSTACLE_COUNT = 12;
    private static final float ASTEROID_PROBABILITY = 0.65f;   // sinon : trou noir


    private static final float LEVEL_LENGTH_FACTOR = 10f;
    private static final float MIN_SPEED_FACTOR = 0.003f;
    private static final float MAX_SPEED_FACTOR = 0.012f;
    private static final float ALERT_DIST_FACTOR = 0.6f;     // fenêtre d'esquive (x hauteur d'écran)

    /* SANTÉ ET SCORE */
    private static final int START_HEALTH = 100;
    private static final int ASTEROID_DAMAGE = 20;           // santé perdue par astéroïde touché
    private static final int EVADE_POINTS = 50;              // points gagnés par obstacle évité
    private static final boolean DEBUG_BUTTONS = true;       // boutons de test (à mettre à false à la fin)

    private float distance, levelLength, speed, minSpeed, maxSpeed, alertDist;
    private boolean thrust;

    private int health;          // santé du vaisseau (0 = détruit)
    private int score;           // points
    private int evadedCount;     // obstacles évités
    private boolean won;         // true = arrivé au point B, false = vaisseau détruit

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
    private final RectF btnAdvance = new RectF();     // bouton de test pour finir la partie
    private final RectF btnTestShake = new RectF();   // test : simule la secousse
    private final RectF btnTestScream = new RectF();  // test : simule le cri
    private final RectF progressBar = new RectF();
    private final RectF btnEnd = new RectF();     // bouton de test pour finir la partie

    private final Settings settings;
    private List<Settings.ScoreEntry> scores = new ArrayList<>();
    private int lastRank = -1;   // place de la partie qui vient de finir (-1 = hors top 5)

    // ---------------- Peintures (créées une seule fois) ----------------
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GameView(Context context) {
        super(context);
        settings = new Settings(context);
        setBackgroundColor(Color.rgb(8, 10, 24));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    /* [PSEUDO] API PUBLIQUE POUR L'ACTIVITÉ */

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public GameState getState() {
        return gameState;
    }

    public RectF getNameFieldRect() {
        return nameFieldRect;
    }

    /** Appelée par l'activité une fois le pseudo validé. */
    public void startPlaying(String name) {
        playerName = name;
        resetGame();
        setState(GameState.PLAYING);
    }

    /** Seul endroit où l'état change : on prévient toujours l'activité. */
    private void setState(GameState newState) {
        if (gameState == newState) return;
        gameState = newState;
        if (newState == GameState.GAME_OVER) {
            lastRank = settings.addScore(playerName, score);
            scores = settings.getScores();
        }
        if (listener != null) listener.onStateChanged(newState);
        invalidate();
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
        if (gameState == GameState.PLAYING) setState(GameState.PAUSED);   // [PSEUDO] setState;
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
        levelLength = h * LEVEL_LENGTH_FACTOR;
        minSpeed = h * MIN_SPEED_FACTOR;
        maxSpeed = h * MAX_SPEED_FACTOR;
        alertDist = h * ALERT_DIST_FACTOR;
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
        btnAdvance.set(w * 0.10f, h * 0.86f, w * 0.90f, h * 0.96f);
        btnTestShake.set(w * 0.04f, h * 0.77f, w * 0.48f, h * 0.83f);
        btnTestScream.set(w * 0.52f, h * 0.77f, w * 0.96f, h * 0.83f);
        progressBar.set(w * 0.10f, h * 0.12f, w * 0.90f, h * 0.135f);   // légèrement plus bas pour laisser la place à santé + score

        textPaint.setTextSize(w * 0.05f);
        strokePaint.setStrokeWidth(w * 0.006f);
        // champ pseudo : même largeur et même hauteur que le bouton JOUER, placé au-dessus
        float gap = h * 0.11f;
        nameFieldRect.set(btn1.left, btn1.top - gap, btn1.right, btn1.bottom - gap);

        resetGame();
    }

    private void resetGame() {
        distance = 0;
        speed = minSpeed;
        thrust = false;
        hitCount = 0;
        hitFlash = 0;
        health = START_HEALTH;
        score = 0;
        evadedCount = 0;
        won = false;
        generateObstacles();}
    private void startNewGame() { resetGame(); gameState = GameState.PLAYING; }
    private void pauseGame() { thrust = false; gameState = GameState.PAUSED; }

    /* MISE À JOUR*/
    private void update() {
        // Les étoiles ne défilent que pendant la partie
        if (gameState != GameState.PLAYING) return;

        // La vitesse tend vers le max si on maintient le bouton, vers le min sinon
        float target = thrust ? maxSpeed : minSpeed;
        speed += (target - speed) * 0.08f;
        distance += speed;

        for (int i = 0; i < STAR_COUNT; i++) {
            starY[i] += speed * starDepth[i];
            if (starY[i] > getHeight()) {       // sortie en bas -> réapparition en haut
                starY[i] = 0;
                starX[i] = rnd.nextFloat() * getWidth();
            }
        }

        // Un obstacle non évité atteint le vaisseau
        for (Obstacle o : obstacles) {
            if (o.resolved) continue;
            float dist = o.pos - distance;
            if (dist <= shipSize) {
                onObstacleHit(o);
            }
        }
        if (hitFlash > 0) hitFlash--;

        // Santé à 0 -> défaite
        if (health <= 0) {
            health = 0;
            won = false;
            thrust = false;
            setState(GameState.GAME_OVER);
        }
        else if (distance >= levelLength) {
            distance = levelLength;
            won = true;
            thrust = false;
            setState(GameState.GAME_OVER);
        }
    }

    /* Un obstacle touche le vaisseau */
    private void onObstacleHit(Obstacle o) {
        o.resolved = true;
        hitCount++;
        hitFlash = 15;
        if (o.type == ObstacleType.ASTEROID) {
            health = Math.max(0, health - ASTEROID_DAMAGE);   // dégâts partiels
        } else {
            health = 0;                                        // trou noir : destruction immédiate
        }
        // TODO plus tard : vibration quand un astéroïde touche le vaisseau
    }

    /* Esquive : évite l'obstacle le plus proche du type demandé, s'il est dans la fenêtre d'esquive.
       Plus tard : appelée par l'accéléromètre (ASTEROID) et par le micro (BLACK_HOLE). */
    private void tryEvade(ObstacleType type) {
        if (gameState != GameState.PLAYING) return;
        Obstacle best = null;
        for (Obstacle o : obstacles) {
            if (o.resolved || o.type != type) continue;
            float dist = o.pos - distance;
            if (dist > 0 && dist < alertDist && (best == null || o.pos < best.pos)) best = o;
        }
        if (best != null) {
            best.resolved = true;
            evadedCount++;
            score += EVADE_POINTS;
        }
    }

    private void generateObstacles() {
        obstacles.clear();
        float h = getHeight();
        float first = h * 1.5f;
        float step = (levelLength - h * 3f) / OBSTACLE_COUNT;
        for (int i = 0; i < OBSTACLE_COUNT; i++) {
            Obstacle o = new Obstacle();
            o.type = rnd.nextFloat() < ASTEROID_PROBABILITY ? ObstacleType.ASTEROID : ObstacleType.BLACK_HOLE;
            o.pos = first + i * step + rnd.nextFloat() * step * 0.4f;
            o.offsetX = (rnd.nextFloat() - 0.5f) * getWidth() * 0.16f;
            obstacles.add(o);
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
        if (thrust) {   // flamme quand on accélère
            fillPaint.setColor(Color.rgb(255, 150, 0));
            canvas.drawCircle(0, shipSize * 1.1f, shipSize * 0.3f, fillPaint);
        }
        canvas.restore();
    }

    private void drawObstacles(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        Obstacle nearestAlert = null;   // obstacle le plus proche dans la fenêtre d'esquive
        for (Obstacle o : obstacles) {
            if (o.resolved) continue;
            float dist = o.pos - distance;
            float oy = shipY - dist;                     // plus l'obstacle est loin, plus il est haut
            float r = w * (o.type == ObstacleType.ASTEROID ? 0.07f : 0.09f);
            if (oy < -r || oy > h + r) continue;         // hors écran : on ne dessine pas
            float ox = shipX + o.offsetX;

            if (o.type == ObstacleType.ASTEROID) {
                fillPaint.setColor(Color.rgb(130, 120, 110));
                canvas.drawCircle(ox, oy, r, fillPaint);
                fillPaint.setColor(Color.rgb(95, 88, 80));   // cratères
                canvas.drawCircle(ox - r * 0.3f, oy - r * 0.2f, r * 0.25f, fillPaint);
                canvas.drawCircle(ox + r * 0.35f, oy + r * 0.3f, r * 0.18f, fillPaint);
            } else {
                fillPaint.setColor(Color.BLACK);
                canvas.drawCircle(ox, oy, r, fillPaint);
                strokePaint.setColor(Color.rgb(150, 60, 220));
                canvas.drawCircle(ox, oy, r, strokePaint);
            }

            // Dans la fenêtre d'esquive : cercle d'alerte autour de l'obstacle
            if (dist > 0 && dist < alertDist) {
                strokePaint.setColor(o.type == ObstacleType.ASTEROID ? Color.YELLOW : Color.MAGENTA);
                canvas.drawCircle(ox, oy, r * 1.4f, strokePaint);
                if (nearestAlert == null || o.pos < nearestAlert.pos) nearestAlert = o;
            }
        }

        // Consigne affichée pour l'obstacle le plus proche
        if (nearestAlert != null) {
            boolean ast = nearestAlert.type == ObstacleType.ASTEROID;
            textPaint.setColor(ast ? Color.YELLOW : Color.MAGENTA);
            canvas.drawText(ast ? "SECOUEZ !" : "CRIEZ !", w / 2f, h * 0.27f, textPaint);
        }
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

    /* Barre de progression : A (départ) ---- B (arrivée) */
    private void drawProgressBar(Canvas canvas) {
        float progress = Math.min(1f, distance / levelLength);

        // Fond de la barre
        fillPaint.setColor(Color.argb(120, 255, 255, 255));
        canvas.drawRoundRect(progressBar, 10, 10, fillPaint);

        // Partie remplie (on modifie temporairement le côté droit du rectangle)
        float fullRight = progressBar.right;
        progressBar.right = progressBar.left + (fullRight - progressBar.left) * progress;
        fillPaint.setColor(Color.rgb(0, 200, 220));
        canvas.drawRoundRect(progressBar, 10, 10, fillPaint);
        progressBar.right = fullRight;   // on remet la bonne valeur

        // Repères A et B
        textPaint.setColor(Color.WHITE);
        float ty = progressBar.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText("A", getWidth() * 0.05f, ty, textPaint);
        canvas.drawText("B", getWidth() * 0.95f, ty, textPaint);
    }

    /* Santé et score (en haut à gauche) */
    private void drawHud(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        textPaint.setTextAlign(Paint.Align.LEFT);

        // Santé : la couleur change selon le niveau
        textPaint.setColor(health > 50 ? Color.rgb(80, 220, 100)
                : health > 25 ? Color.rgb(250, 190, 0) : Color.RED);
        canvas.drawText("Santé : " + health, w * 0.04f, h * 0.055f, textPaint);

        // Score
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Score : " + score, w * 0.04f, h * 0.095f, textPaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /* Ligne de texte centrée (écran de fin) */
    private void drawLine(Canvas canvas, String text, float y) {
        textPaint.setColor(Color.WHITE);
        canvas.drawText(text, getWidth() / 2f, y, textPaint);
    }

    /* Écran Accueil */
    private void drawStartScreen(Canvas canvas) {
        drawTitle(canvas, "SPACE ESCAPE", getHeight() * 0.28f);

        // libellé du champ pseudo
        textPaint.setColor(Color.rgb(0, 200, 220));
        canvas.drawText("Pseudo", getWidth() / 2f,
                nameFieldRect.top - getHeight() * 0.012f, textPaint);

        drawButton(canvas, btn1, "JOUER", Color.rgb(40, 140, 70));
    }

    /* Écran Partie */
    private void drawGame(Canvas canvas) {
        drawObstacles(canvas);
        drawShip(canvas);
        if (hitFlash > 0) {
            fillPaint.setColor(Color.RED);
            fillPaint.setAlpha(hitFlash * 8);
            canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
            fillPaint.setAlpha(255);
        }

        drawHud(canvas);   // santé et score

        // Compteur d'impacts : désactivé pour l'instant
        // textPaint.setColor(Color.WHITE);
        // textPaint.setTextAlign(Paint.Align.LEFT);
        // canvas.drawText("Impacts : " + hitCount, getWidth() * 0.04f, getHeight() * 0.135f, textPaint);
        // textPaint.setTextAlign(Paint.Align.CENTER);

        drawButton(canvas, btnPause, "PAUSE", Color.argb(180, 80, 80, 100));
        drawProgressBar(canvas);
        if (DEBUG_BUTTONS) {
            drawButton(canvas, btnTestShake, "test : secouer", Color.argb(160, 120, 120, 0));
            drawButton(canvas, btnTestScream, "test : crier", Color.argb(160, 120, 0, 120));
        }
        drawButton(canvas, btnAdvance, "AVANCER (maintenir)", thrust ? Color.rgb(230, 120, 0) : Color.rgb(40, 90, 200));
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
        int h = getHeight();
        drawTitle(canvas, won ? "MISSION RÉUSSIE" : "VAISSEAU DÉTRUIT", h * 0.10f);
        drawLine(canvas, "Score : " + score, h * 0.16f);
        drawLine(canvas, "Obstacles évités : " + evadedCount, h * 0.20f);
        drawLine(canvas, "Santé restante : " + health, h * 0.24f);
        drawScoreboard(canvas);
        drawButton(canvas, btn1, "Rejouer", Color.rgb(40, 140, 70));
        drawButton(canvas, btn2, "Accueil", Color.rgb(40, 90, 200));
    }

    private void drawScoreboard(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        textPaint.setColor(Color.rgb(0, 200, 220));
        canvas.drawText("MEILLEURS SCORES", w / 2f, h * 0.31f, textPaint);

        for (int i = 0; i < scores.size(); i++) {
            Settings.ScoreEntry e = scores.get(i);
            float y = h * (0.35f + 0.04f * i);
            // la ligne de la partie qui vient de finir est en jaune
            textPaint.setColor(i == lastRank ? Color.rgb(255, 215, 0) : Color.WHITE);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText((i + 1) + ". " + e.name, w * 0.12f, y, textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.valueOf(e.score), w * 0.88f, y, textPaint);
        }
        textPaint.setTextAlign(Paint.Align.CENTER);   // on remet l'alignement par défaut
    }

    /* NAVIGATION ENTRE LES ÉCRANS */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int masked = event.getActionMasked();
        int idx = event.getActionIndex();
        boolean isDown = masked == MotionEvent.ACTION_DOWN || masked == MotionEvent.ACTION_POINTER_DOWN;
        boolean isUp = masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_POINTER_UP;
        float tx = event.getX(idx);
        float ty = event.getY(idx);

        switch (gameState) {
            case START:
                if (btn1.contains(tx, ty)) {
                    // [PSEUDO] c'est l'activité qui valide le pseudo, puis rappelle startPlaying()
                    if (listener != null) listener.onPlayRequested();
                    else startPlaying(Settings.DEFAULT_NAME);
                } else if (listener != null) {
                    listener.onBackgroundTapped();   // ferme le clavier
                }
                break;

            case PLAYING:
                if (btnPause.contains(tx, ty)) setState(GameState.PAUSED);
                else if (btnEnd.contains(tx, ty)) setState(GameState.GAME_OVER);
                break;

            case PAUSED:
                if (btn1.contains(tx, ty)) setState(GameState.PLAYING);        // reprendre
                    // onTouchEvent, case PAUSED
                else if (btn2.contains(tx, ty)) { resetGame(); setState(GameState.PLAYING); }   // recommencer   // recommencer (rien à réinitialiser pour l'instant)
                else if (btn3.contains(tx, ty)) setState(GameState.START);     // abandonner
                break;

            case GAME_OVER:
                // onTouchEvent, case GAME_OVER
                if (btn1.contains(tx, ty)) { resetGame(); setState(GameState.PLAYING); }        // rejouer        // rejouer
                else if (btn2.contains(tx, ty)) setState(GameState.START);     // accueil
                break;
        }
        return true;
    }
}