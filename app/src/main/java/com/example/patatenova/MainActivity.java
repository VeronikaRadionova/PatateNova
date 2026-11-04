package com.example.patatenova;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import com.example.patatenova.GameView;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements GameView.Listener{

    private GameView gameView;
    private EditText nameField;
    private FrameLayout root;
    private Settings settings;
    private MicMonitor micMonitor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = new Settings(this);

        gameView = new GameView(this);
        gameView.setListener(this);
        micMonitor = new MicMonitor();

        nameField = createNameField();

        // Le conteneur peut prendre le focus : c'est ce qui permet de "retirer" le focus
        // du champ de texte (sinon Android le redonne au seul champ focusable de l'écran).
        root = new FrameLayout(this);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        root.addView(gameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Ajouté après GameView => dessiné et touché par-dessus.
        root.addView(nameField, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        // Quand GameView est mesurée (ou change de taille), on cale le champ sur son rectangle.
        gameView.addOnLayoutChangeListener((v, left, top, right, bottom,
                                            oldLeft, oldTop, oldRight, oldBottom) ->
                v.post(this::positionNameField));

        applyState(gameView.getState());   // état initial : START => champ visible
        root.requestFocus();               // aucun focus sur le champ au démarrage
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.startGame();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseFocus();                    // ferme le clavier s'il est ouvert
        micMonitor.stop();
        gameView.onActivityPause();
    }
    @Override
    protected void onStop() {
        super.onStop();
        nameField.setText("");
    }



    /* ---------------- Création et placement du champ ---------------- */

    // Réponse à la demande de permission : on lance la partie dans tous les cas.
    // Si c'est refusé, GameView affiche le bouton "test : crier" à la place du micro.
    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                gameView.setMic(granted ? micMonitor : null);
                beginGame();
            });
    private EditText createNameField() {
        EditText field = new EditText(this);
        field.setHint("Patate");
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setMaxLines(1);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Settings.MAX_PSEUDO_LENGTH)});

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }

        // Apparence "cockpit" : fond bleu nuit translucide, contour néon cyan
        field.setBackground(fieldBackground(false));
        field.setOnFocusChangeListener((v, hasFocus) -> field.setBackground(fieldBackground(hasFocus)));
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.argb(150, 170, 210, 230));
        field.setHighlightColor(Color.argb(90, 0, 200, 220));   // couleur de la sélection de texte
        field.setGravity(Gravity.CENTER);                       // centré comme le texte des boutons
        field.setPadding(dp(12), 0, dp(12), 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {   // curseur cyan
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(Color.rgb(0, 230, 255));
            cursor.setSize(dp(2), dp(20));
            field.setTextCursorDrawable(cursor);
        }

        // Bouton "Terminé" du clavier : on ferme le clavier, on ne lance PAS la partie.
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                releaseFocus();
                return true;
            }
            return false;
        });
        return field;
    }

    /** Aligne l'EditText sur le rectangle réservé par GameView (proportionnel à l'écran). */
    private void positionNameField() {
        RectF r = gameView.getNameFieldRect();
        if (r.isEmpty()) return;           // GameView pas encore dimensionnée

        int w = Math.round(r.width());
        int h = Math.round(r.height());
        int left = Math.round(r.left);
        int top = Math.round(r.top);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) nameField.getLayoutParams();
        // Garde-fou : ne rien faire si rien n'a changé (évite de redéclencher une mise en page pour rien)
        if (lp.width == w && lp.height == h && lp.leftMargin == left && lp.topMargin == top) return;

        lp.gravity = Gravity.TOP | Gravity.START;
        lp.width = w;
        lp.height = h;
        lp.leftMargin = left;
        lp.topMargin = top;
        nameField.setLayoutParams(lp);
        nameField.setTextSize(TypedValue.COMPLEX_UNIT_PX, gameView.getWidth() * 0.05f);
    }

    /* ---------------- Réactions aux événements de GameView ---------------- */

    @Override
    public void onStateChanged(GameView.GameState newState) {
        applyState(newState);
        if (newState == GameView.GameState.PLAYING && hasMicPermission()) {
            gameView.setMic(micMonitor.start() ? micMonitor : null);   // null : micro indisponible, bouton de test
        } else {
            micMonitor.stop();
        }
    }

    @Override
    public void onPlayRequested() {
        // 1. nettoyer et enregistrer la saisie ("" si vide)
        String cleaned = Settings.cleanPseudo(nameField.getText().toString());
        settings.savePseudo(cleaned);

        // 2. fermer le clavier
        releaseFocus();

        if (hasMicPermission()) {
            gameView.setMic(micMonitor);
            beginGame();
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);   // beginGame() est appelé dans la réponse
        }
    }
    private void beginGame() {
        gameView.startPlaying(settings.getDisplayName());
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onBackgroundTapped() {
        releaseFocus();                    // un appui ailleurs ferme le clavier
    }

    /* ---------------- Utilitaires ---------------- */

    /** Le champ n'existe que sur l'écran d'accueil. */
    private void applyState(GameView.GameState state) {
        if (state == GameView.GameState.START) {
            nameField.setText("");
            nameField.setVisibility(View.VISIBLE);
        } else {
            nameField.setVisibility(View.GONE);
            releaseFocus();
        }
    }

    /** Retire le focus du champ (le donne au conteneur) et ferme le clavier. */
    private void releaseFocus() {
        nameField.clearFocus();
        root.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        }
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private GradientDrawable fieldBackground(boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(focused ? Color.argb(210, 10, 28, 60) : Color.argb(170, 10, 20, 48));
        bg.setCornerRadius(24f);   // même arrondi que drawButton() dans GameView
        bg.setStroke(dp(focused ? 3 : 2),
                focused ? Color.rgb(0, 230, 255) : Color.argb(170, 0, 200, 220));
        return bg;
    }
}