package com.example.patatenova;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/** Détecte une secousse du téléphone grâce à l'accéléromètre. */
public class ShakeDetector implements SensorEventListener {

    /** Appelé à chaque secousse détectée. */
    public interface OnShakeListener {
        void onShake();
    }

    // force minimale pour compter comme une secousse, en "g" (1 g = téléphone immobile)
    // plus la valeur est haute, plus il faut secouer fort
    private static final float SHAKE_THRESHOLD_G = 2.3f;
    // délai minimal entre deux secousses: une seule secousse ne compte qu'une fois
    private static final long MIN_INTERVAL_MS = 500;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;       // null = le téléphone n'a pas d'accéléromètre
    private OnShakeListener listener;
    private boolean running;
    private long lastShakeMs;

    public ShakeDetector(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = (sensorManager != null)
                ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                : null;
    }

    public void setListener(OnShakeListener listener) {
        this.listener = listener;
    }

    /** Renvoie false si le téléphone n'a pas d'accéléromètre. */
    public boolean start() {
        if (accelerometer == null) return false;
        if (running) return true;
        running = sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        return running;
    }

    public void stop() {
        if (running) {
            sensorManager.unregisterListener(this);
            running = false;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // force totale subie par le téléphone, en g (indépendante de l'orientation)
        float gx = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gy = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gz = event.values[2] / SensorManager.GRAVITY_EARTH;
        float gForce = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);

        if (gForce > SHAKE_THRESHOLD_G) {
            long now = SystemClock.uptimeMillis();
            if (now - lastShakeMs > MIN_INTERVAL_MS) {
                lastShakeMs = now;
                if (listener != null) listener.onShake();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // rien à faire
    }
}