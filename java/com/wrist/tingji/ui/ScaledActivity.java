package com.wrist.tingji.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import com.wrist.tingji.core.Settings;

/** Applies the saved UI scale before any view is inflated. */
public class ScaledActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        try {
            float scale = new Settings(newBase).getUiScaleActual();
            if (Math.abs(scale - 1f) < 0.001f) {
                super.attachBaseContext(newBase);
                return;
            }
            Configuration cfg = new Configuration(newBase.getResources().getConfiguration());
            cfg.densityDpi = Math.max(72, Math.round(cfg.densityDpi * scale));
            super.attachBaseContext(newBase.createConfigurationContext(cfg));
        } catch (Throwable t) {
            super.attachBaseContext(newBase);
        }
    }
}
