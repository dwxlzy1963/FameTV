package com.fametv.app.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.fametv.app.model.Channel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tiny SharedPreferences wrapper for favorites + history + settings.
 */
public class Prefs {

    private static final String NAME = "fametv_prefs";
    private static final String KEY_FAVORITES = "favorites_json";
    private static final String KEY_HISTORY = "history_json";
    private static final String MAX_HISTORY = "20";

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

    public void toggleFavorite(@NonNull Channel channel) {
        List<Channel> list = getFavorites();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(channel.getId())) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) list.add(0, channel);
        sp.edit().putString(KEY_FAVORITES, gson.toJson(list)).apply();
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
        // Remove existing occurrences to keep recency order
        Set<String> seen = new HashSet<>();
        List<Channel> deduped = new ArrayList<>();
        // Put newest first
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
}
