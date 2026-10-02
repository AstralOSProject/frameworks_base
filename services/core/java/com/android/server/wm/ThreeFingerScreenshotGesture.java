/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.wm;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.PointerEventListener;
import android.view.WindowManager;

/**
 * Detects a quick three-finger downward swipe anywhere on the display and
 * triggers a fullscreen screenshot through {@link DisplayPolicy}.
 *
 * <p>Registered on the display's {@link PointerEventDispatcher} (same mechanism
 * as {@link SystemGesturesPointerEventListener}), so it observes all touches
 * regardless of which window consumes them.
 */
final class ThreeFingerScreenshotGesture implements PointerEventListener {

    private static final int MIN_POINTERS = 3;
    private static final long SWIPE_TIMEOUT_MS = 500;

    private final Context mContext;
    private final DisplayPolicy mDisplayPolicy;
    private final int mDistanceThreshold;

    private boolean mArmed;
    private boolean mTriggered;
    private float mStartX;
    private float mStartY;
    private long mArmTimeMs;

    ThreeFingerScreenshotGesture(Context context, DisplayPolicy displayPolicy) {
        mContext = context;
        mDisplayPolicy = displayPolicy;
        mDistanceThreshold = context.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.system_gestures_distance_threshold);
    }

    @Override
    public void onPointerEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.getPointerCount() >= MIN_POINTERS && !mArmed && !mTriggered
                        && isFeatureEnabled()) {
                    mArmed = true;
                    mStartX = centroidX(event);
                    mStartY = centroidY(event);
                    mArmTimeMs = SystemClock.uptimeMillis();
                }
            }
            case MotionEvent.ACTION_MOVE -> {
                if (mArmed && !mTriggered) {
                    if (SystemClock.uptimeMillis() - mArmTimeMs > SWIPE_TIMEOUT_MS) {
                        mArmed = false;
                        break;
                    }
                    final float dy = centroidY(event) - mStartY;
                    final float dx = centroidX(event) - mStartX;
                    if (dy >= mDistanceThreshold && dy > Math.abs(dx)) {
                        mArmed = false;
                        mTriggered = true;
                        mDisplayPolicy.takeScreenshot(
                                WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
                                WindowManager.ScreenshotSource.SCREENSHOT_OTHER);
                    }
                }
            }
            case MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerCount() - 1 < MIN_POINTERS) {
                    mArmed = false;
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mArmed = false;
                mTriggered = false;
            }
            default -> { }
        }
    }

    private boolean isFeatureEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ASTRAL_THREE_FINGER_SCREENSHOT, 1) == 1;
    }

    private float centroidX(MotionEvent event) {
        return centroid(event, true);
    }

    private float centroidY(MotionEvent event) {
        return centroid(event, false);
    }

    private float centroid(MotionEvent event, boolean useX) {
        final int count = Math.min(MIN_POINTERS, event.getPointerCount());
        float sum = 0f;
        for (int i = 0; i < count; i++) {
            sum += useX ? event.getX(i) : event.getY(i);
        }
        return sum / count;
    }
}
