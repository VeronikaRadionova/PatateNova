package com.example.patatenova;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
public class Settings {
    public static final String DEFAULT_NAME = "Invité";
    public static final int MAX_PSEUDO_LENGTH = 12;

    private static final String PREFS_NAME = "patatenova_prefs";
    private static final String KEY_PSEUDO = "pseudo";

    public static final int MAX_SCORES = 5;
    private static final String KEY_SCORES = "scores";
    private final SharedPreferences prefs;

    public Settings(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Nettoie la saisie : espaces au début/fin supprimés, espaces multiples réduits à un seul. */
    public static String cleanPseudo(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    /** Enregistre le pseudo tel que saisi (nettoyé). Chaîne vide autorisée : "Invité" n'est PAS stocké. */
    public void savePseudo(String cleaned) {
        prefs.edit().putString(KEY_PSEUDO, cleaned == null ? "" : cleaned).apply();
    }

    /** Dernier pseudo saisi, "" si aucun. Sert à préremplir le champ. */
    public String getPseudo() {
        return prefs.getString(KEY_PSEUDO, "");
    }

    /** Nom à afficher / à mettre au classement : le pseudo, ou "Invité" s'il est vide. */
    public String getDisplayName() {
        String pseudo = getPseudo();
        return pseudo.isEmpty() ? DEFAULT_NAME : pseudo;
    }
    public static class ScoreEntry {
        public final String name;
        public final int score;
        public ScoreEntry(String name, int score) {
            this.name = name;
            this.score = score;
        }
    }

    /** Classement trié du meilleur au moins bon (5 entrées maximum). */
    public List<ScoreEntry> getScores() {
        List<ScoreEntry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_SCORES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new ScoreEntry(o.getString("name"), o.getInt("score")));
            }
        } catch (JSONException e) {
            // données illisibles : on repart d'un classement vide
        }
        return list;
    }

    /** Ajoute un score et garde les 5 meilleurs.
     *  Renvoie la place (0 = premier) de la nouvelle entrée, ou -1 si elle n'est pas dans le top 5.
     *  À égalité, l'ancien score reste devant. */
    public int addScore(String name, int score) {
        List<ScoreEntry> list = getScores();
        int pos = list.size();
        for (int i = 0; i < list.size(); i++) {
            if (score > list.get(i).score) { pos = i; break; }
        }
        list.add(pos, new ScoreEntry(name, score));
        while (list.size() > MAX_SCORES) list.remove(list.size() - 1);

        try {
            JSONArray arr = new JSONArray();
            for (ScoreEntry e : list) {
                arr.put(new JSONObject().put("name", e.name).put("score", e.score));
            }
            prefs.edit().putString(KEY_SCORES, arr.toString()).apply();
        } catch (JSONException e) {
            return -1;
        }
        return pos < MAX_SCORES ? pos : -1;
    }

}
