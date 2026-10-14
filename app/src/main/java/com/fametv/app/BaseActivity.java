package com.fametv.app;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fametv.app.storage.Prefs;

/**
 * Base activity that applies the user's saved language preference before
 * inflating any views. All FameTV activities extend this so that changing
 * the language in Settings takes effect on the next navigation.
 *
 * Note: changing Locale on an already-running process via
 * {@code getResources().updateConfiguration()} does not retroactively
 * change the strings of already-inflated views — that's why we call
 * {@code recreate()} from SettingsActivity after the user changes the language.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Prefs.get(this).applyLanguage(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        // Apply the saved locale BEFORE the activity inflates any views
        Prefs prefs = Prefs.get(newBase);
        android.content.Context ctx = newBase;
        String code = prefs.getLanguage();
        if (!com.fametv.app.storage.Prefs.LANG_FOLLOW_SYSTEM.equals(code)) {
            java.util.Locale locale = com.fametv.app.storage.Prefs.LANG_CHINESE.equals(code)
                    ? java.util.Locale.SIMPLIFIED_CHINESE
                    : java.util.Locale.ENGLISH;
            android.content.res.Configuration cfg = new android.content.res.Configuration(
                    newBase.getResources().getConfiguration());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                cfg.setLocale(locale);
                ctx = newBase.createConfigurationContext(cfg);
            } else {
                //noinspection deprecation
                cfg.locale = locale;
                //noinspection deprecation
                newBase.getResources().updateConfiguration(cfg, newBase.getResources().getDisplayMetrics());
            }
            java.util.Locale.setDefault(locale);
        }
        super.attachBaseContext(ctx);
    }
}
