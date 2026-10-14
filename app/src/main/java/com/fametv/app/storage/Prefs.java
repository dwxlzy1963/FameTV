package com.fametv.app.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;

import com.fametv.app.model.Channel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tiny SharedPreferences wrapper for favorites + history + settings.
 * Also persists the user's language preference and applies it on demand.
 */
public class Prefs {

    private static final String NAME = "fametv_prefs";
    private static final String KEY_FAVORITES = "favorites_json";
    private static final String KEY_HISTORY = "history_json";
    private static final String KEY_LANGUAGE = "user_language";
    private static final String MAX_HISTORY = "20";

    /** Language code values stored in prefs. "" = follow system. */
    public static final String LANG_FOLLOW_SYSTEM = "";
    public static final String LANG_ENGLISH = "en";
    public static final String LANG_CHINESE = "zh-CN";

    private static Prefs INSTANCE;
    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    private Prefs(@NonNull Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(@NonNull Context ctx) {
        if (INSTANCE == null) INSTANCE = new Prefs(ctx);
        return INSTANCE;
    }

    // ---------------- Favorites ----------------

    public List<Channel> getFavorites() {
        String json = sp.getString(KEY_FAVORITES, "[]");
        Type t = new TypeToken<List<Channel>>(){}.getType();
        List<Channel> list = gson.fromJson(json, t);
        return list == null ? new ArrayList<>() : list;
    }

    public boolean isFavorite(@NonNull String channelId) {
        for (Channel c : getFavorites()) {
            if (channelId.equals(c.getId())) return true;
        }
        return false;
    }

    /** Toggle favorite. Returns TRUE if the channel is now a favorite, FALSE otherwise. */
    public boolean toggleFavorite(@NonNull Channel channel) {
        List<Channel> list = getFavorites();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (channel.getId() != null && channel.getId().equals(list.get(i).getId())) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) list.add(0, channel);
        sp.edit().putString(KEY_FAVORITES, gson.toJson(list)).apply();
        return !removed;
    }

    // ---------------- History ----------------

    public List<Channel> getHistory() {
        String json = sp.getString(KEY_HISTORY, "[]");
        Type t = new TypeToken<List<Channel>>(){}.getType();
        List<Channel> list = gson.fromJson(json, t);
        return list == null ? new ArrayList<>() : list;
    }

    public void addToHistory(@NonNull Channel channel) {
        List<Channel> list = getHistory();
        Set<String> seen = new HashSet<>();
        List<Channel> deduped = new ArrayList<>();
        deduped.add(channel);
        seen.add(channel.getId());
        for (Channel c : list) {
            if (seen.add(c.getId())) {
                deduped.add(c);
            }
            if (deduped.size() >= Integer.parseInt(MAX_HISTORY)) break;
        }
        sp.edit().putString(KEY_HISTORY, gson.toJson(deduped)).apply();
    }

    public void clearHistory() {
        sp.edit().putString(KEY_HISTORY, "[]").apply();
    }

    // ---------------- Language ----------------

    public String getLanguage() {
        return sp.getString(KEY_LANGUAGE, LANG_FOLLOW_SYSTEM);
    }

    public void setLanguage(@NonNull String code) {
        sp.edit().putString(KEY_LANGUAGE, code).apply();
    }

    /** Apply the saved language preference to the given context's resources. */
    public void applyLanguage(@NonNull Context ctx) {
        String code = getLanguage();
        Locale locale;
        if (LANG_CHINESE.equals(code)) {
            locale = Locale.SIMPLIFIED_CHINESE;
        } else if (LANG_ENGLISH.equals(code)) {
            locale = Locale.ENGLISH;
        } else {
            // Follow system
            locale = Resources.getSystem().getConfiguration().getLocales().get(0);
        }
        Locale.setDefault(locale);
        Configuration cfg = new Configuration(ctx.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            cfg.setLocale(locale);
        } else {
            //noinspection deprecation
            cfg.locale = locale;
        }
        ctx.getResources().updateConfiguration(cfg, ctx.getResources().getDisplayMetrics());
    }
}
