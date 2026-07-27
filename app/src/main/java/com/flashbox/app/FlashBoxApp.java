package com.flashbox.app;

import android.app.Application;
import android.content.Context;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

/**
 * FlashBox application entry point.
 *
 * Centralizes:
 *   - WebView data directory bootstrap (called early so first WebView is fast).
 *   - Theme / locale defaults.
 *   - Global config initialization.
 */
public class FlashBoxApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = this;
        // Force app-default locale / theme.
        AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        // Initialize preferences (defaults get applied here).
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // WebView warmup (best-effort; ignore failures on older OS).
        try {
            WebView.setDataDirectorySuffix("flashbox");
        } catch (Throwable t) {
            // Some devices throw on second call; safe to ignore.
        }
    }

    public static Context appContext() {
        return sContext;
    }

    private static Context sContext;
}
