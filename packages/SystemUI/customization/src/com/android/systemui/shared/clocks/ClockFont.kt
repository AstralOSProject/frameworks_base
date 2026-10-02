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
package com.android.systemui.shared.clocks

import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import android.provider.Settings

/** Resolves the AstralOS lockscreen clock font family setting. */
object ClockFont {

    fun currentFamily(context: Context): String {
        // getCurrentUser() needs INTERACT_ACROSS_USERS, which SystemUI has but
        // WallpaperPicker2/ThemePicker does not; fall back to the calling
        // process's own user there (same pattern as ClockRegistry.querySettings).
        return try {
            Settings.Secure.getStringForUser(
                    context.contentResolver,
                    Settings.Secure.ASTRAL_CLOCK_FONT,
                    ActivityManager.getCurrentUser(),
            ) ?: ""
        } catch (e: SecurityException) {
            Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ASTRAL_CLOCK_FONT,
            ) ?: ""
        }
    }

    /**
     * Returns the typeface for the configured clock font family, or null when
     * the system default (as defined by the clock layout) should be kept.
     */
    fun resolveTypeface(context: Context): Typeface? {
        val family = currentFamily(context)
        if (family.isEmpty()) {
            return null
        }
        return Typeface.create(family, Typeface.NORMAL)
    }
}
