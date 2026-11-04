package com.example.patatenova;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.os.SystemClock;

/** Écoute le micro : volume (0 à 1), bruit ambiant appris et seuil de cri. */
public class MicMonitor {

    private static final int SAMPLE_RATE = 44100;
    private static final int CHUNK = 1024;            // environ 23 ms de son par lecture

    // Échelle du volume : -60 dBFS (silence) -> 0, -10 dBFS (très fort) -> 1
    private static final float DB_FLOOR = -60f;
    private static final float DB_CEIL = -10f;

    // Seuil = bruit ambiant + marge. Marge de 0.45 (sensibilité 0) à 0.20 (sensibilité 1).
    private static final float MARGIN_MAX = 0.45f;
    private static final float MARGIN_MIN = 0.20f;
    private static final float MIN_THRESHOLD = 0.50f;  // jamais en dessous (évite de déclencher en parlant)
    private static final float MAX_THRESHOLD = 0.90f;  // jamais au-dessus (reste atteignable dans le bruit)
    private static final long CALIBRATION_MS = 600;    // apprentissage rapide du bruit au démarrage

    private AudioRecord record;
    private Thread thread;
    private volatile boolean running;
    private volatile float level;                      // volume lissé, 0 à 1
    private volatile float noiseFloor = 0.15f;         // bruit ambiant, 0 à 1
    private volatile float sensitivity = 0.5f;         // 0 = difficile, 1 = facile
    private volatile long calibUntil;

    /** Renvoie false si le micro est indisponible. La permission doit être vérifiée avant. */
    @SuppressLint("MissingPermission")
    public synchronized boolean start() {
        if (running) return true;
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) return false;

        AudioRecord rec = null;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, CHUNK * 8));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                rec.release();
                return false;
            }
            rec.startRecording();
        } catch (RuntimeException e) {
            if (rec != null) rec.release();
            return false;
        }

        final AudioRecord recorder = rec;
        record = recorder;
        level = 0f;
        calibUntil = SystemClock.uptimeMillis() + CALIBRATION_MS;
        running = true;
        thread = new Thread(() -> loop(recorder), "mic-monitor");
        thread.start();
        return true;
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(300); } catch (InterruptedException ignored) { }
            thread = null;
        }
        if (record != null) {
            try { record.stop(); } catch (IllegalStateException ignored) { }
            record.release();
            record = null;
        }
        level = 0f;
    }

    private void loop(AudioRecord rec) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        short[] buf = new short[CHUNK];
        float smooth = 0f;

        while (running) {
            int n = rec.read(buf, 0, CHUNK);
            if (n < 0) break;
            if (n == 0) continue;

            // Volume efficace (RMS) converti en décibels puis ramené à 0..1
            double sum = 0;
            for (int i = 0; i < n; i++) {
                double s = buf[i] / 32768.0;
                sum += s * s;
            }
            double rms = Math.sqrt(sum / n);
            double db = 20 * Math.log10(Math.max(rms, 1e-6));
            float raw = (float) Math.max(0, Math.min(1, (db - DB_FLOOR) / (DB_CEIL - DB_FLOOR)));

            // Lissage : monte vite, retombe doucement
            smooth += (raw - smooth) * (raw > smooth ? 0.6f : 0.15f);
            level = smooth;

            // Bruit ambiant : descend vite quand c'est calme, monte très lentement
            // (un cri de 1 seconde ne le fait pratiquement pas bouger)
            float floor = noiseFloor;
            boolean calibrating = SystemClock.uptimeMillis() < calibUntil;
            float rate = calibrating ? 0.15f : (raw < floor ? 0.1f : 0.002f);
            noiseFloor = floor + (raw - floor) * rate;
        }
    }

    public boolean isRunning() { return running; }

    /** Faux pendant la première fraction de seconde (apprentissage du bruit ambiant). */
    public boolean isReady() { return running && SystemClock.uptimeMillis() >= calibUntil; }

    public float getLevel() { return level; }

    public float getThreshold() {
        float margin = MARGIN_MAX - sensitivity * (MARGIN_MAX - MARGIN_MIN);
        return Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, noiseFloor + margin));
    }

    /** 0 = il faut crier très fort, 1 = un cri modéré suffit. */
    public void setSensitivity(float s) { sensitivity = Math.max(0f, Math.min(1f, s)); }
}
