package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.qualifiers.Application;

import java.util.ArrayDeque;

import javax.inject.Inject;

public class ShakeFlashlightStartable implements CoreStartable, SensorEventListener {

    private static final float SHAKE_DEVIATION = 4.0f;
    private static final int HITS_REQUIRED = 2;
    private static final long WINDOW_MS = 500;
    private static final long DEBOUNCE_MS = 1500;

    private final Context mContext;
    private final FlashlightController mFlashlight;
    private final SensorManager mSensorManager;
    private final PowerManager mPowerManager;
    private final ArrayDeque<Sample> mWindow = new ArrayDeque<>();

    private Sensor mAccelerometer;
    private boolean mListening;
    private long mLastTrigger;

    private final ContentObserver mSettingObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    updateListening();
                }
            };

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateListening();
        }
    };

    @Inject
    public ShakeFlashlightStartable(
            @Application Context context, FlashlightController flashlight) {
        mContext = context;
        mFlashlight = flashlight;
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public void start() {
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mAccelerometer == null) {
            return;
        }
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ASTRAL_SHAKE_FLASHLIGHT),
                false, mSettingObserver);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mScreenReceiver, filter);
        updateListening();
    }

    private void updateListening() {
        boolean enabled = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ASTRAL_SHAKE_FLASHLIGHT, 0) == 1;
        boolean shouldListen = enabled && mPowerManager.isInteractive();
        if (shouldListen == mListening) {
            return;
        }
        mListening = shouldListen;
        if (mListening) {
            mWindow.clear();
            mSensorManager.registerListener(
                    this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        } else {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        float deviation = Math.abs(magnitude - SensorManager.GRAVITY_EARTH);
        long now = SystemClock.elapsedRealtime();

        mWindow.addLast(new Sample(now, deviation));
        while (!mWindow.isEmpty() && now - mWindow.peekFirst().time > WINDOW_MS) {
            mWindow.pollFirst();
        }

        int hits = 0;
        for (Sample sample : mWindow) {
            if (sample.deviation >= SHAKE_DEVIATION) {
                hits++;
            }
        }
        if (hits < HITS_REQUIRED || now - mLastTrigger < DEBOUNCE_MS) {
            return;
        }
        mLastTrigger = now;
        mWindow.clear();
        toggleFlashlight();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void toggleFlashlight() {
        if (!mFlashlight.isAvailable()) {
            return;
        }
        mFlashlight.setFlashlight(!mFlashlight.isEnabled());
    }

    private static final class Sample {
        final long time;
        final float deviation;

        Sample(long time, float deviation) {
            this.time = time;
            this.deviation = deviation;
        }
    }
}
