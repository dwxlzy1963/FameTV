package com.fametv.app.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fametv.app.model.ChannelListResponse;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches the remote channels.json (and falls back to a bundled copy under res/raw).
 * Caches to a local file so the launcher can render instantly on next boot,
 * then refreshes in the background if TTL expired.
 */
public class ChannelRepository {

    private static final String TAG = "ChannelRepository";
    private static final String PREFS_NAME = "fametv_repo";
    private static final String KEY_LAST_FETCH = "last_fetch_ms";
    private static final String KEY_LAST_VERSION = "last_version";

    /** Cache TTL — refresh in background if older than 24h. */
    private static final long CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24);

    /**
     * Default remote URL. After you host channels.json on your own Gist / GitHub Pages / server,
     * change this in Settings or replace the value below before building the APK.
     * Format the URL as raw text — must serve the channels.json produced by build_channels_json.py.
     */
    private static final String DEFAULT_REMOTE_URL =
            "https://raw.githubusercontent.com/famelack/famelack-data/main/tv/raw/countries_metadata.json";

    /**
     * Bundled fallback channels.json (res/raw/channels_default.json) — used only when both
     * the remote URL and local cache fail. Replace with a fuller snapshot before building
     * if you want the offline-first experience.
     */
    private static final int BUNDLED_FALLBACK_RES_ID = com.fametv.app.R.raw.channels_default;

    private static ChannelRepository INSTANCE;

    private final Context context;
    private final OkHttpClient http;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();

    /** User-configurable remote URL for channels.json. */
    private String remoteUrl;

    public interface Callback {
        void onDataLoaded(@NonNull ChannelListResponse response, boolean fromCache);
        void onError(@NonNull String message, @androidx.annotation.Nullable ChannelListResponse cached);
    }

    private ChannelRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        File cacheDir = new File(this.context.getCacheDir(), "okhttp");
        cacheDir.mkdirs();
        this.http = new OkHttpClient.Builder()
                .cache(new Cache(cacheDir, 50L * 1024 * 1024))   // 50 MB HTTP cache
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        SharedPreferences prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.remoteUrl = prefs.getString("remote_url", DEFAULT_REMOTE_URL);
    }

    public static synchronized ChannelRepository get(@NonNull Context context) {
        if (INSTANCE == null) {
            INSTANCE = new ChannelRepository(context);
        }
        return INSTANCE;
    }

    public String getRemoteUrl() { return remoteUrl; }

    public void setRemoteUrl(@NonNull String url) {
        this.remoteUrl = url;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("remote_url", url)
                .apply();
    }

    /** Returns cached file or null. */
    private File cacheFile() {
        return new File(context.getFilesDir(), "channels_cache.json");
    }

    /** Load cached JSON synchronously (blocking — call off main thread). */
    public ChannelListResponse loadCached() {
        File f = cacheFile();
        if (!f.exists()) return loadBundledFallback();
        try {
            String json = readFileToString(f);
            return gson.fromJson(json, ChannelListResponse.class);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read cache: " + e.getMessage());
            return loadBundledFallback();
        }
    }

    /** Load the bundled res/raw/channels_default.json as last-resort fallback. */
    private ChannelListResponse loadBundledFallback() {
        try {
            InputStream is = context.getResources().openRawResource(BUNDLED_FALLBACK_RES_ID);
            String json = readStreamToString(is);
            ChannelListResponse r = gson.fromJson(json, ChannelListResponse.class);
            Log.i(TAG, "Using bundled fallback channels.json (" + (r != null ? r.getChannelCount() : 0) + " channels)");
            return r;
        } catch (Exception e) {
            Log.e(TAG, "Bundled fallback failed: " + e.getMessage());
            return null;
        }
    }

    /** Read a file fully into a String (UTF-8). Uses classic IO (no NIO Files API). */
    private String readFileToString(@NonNull File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            return readStreamToString(fis);
        }
    }

    /** Read an InputStream fully into a String (UTF-8) and close it. */
    private String readStreamToString(@NonNull InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
        is.close();
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Write a String to a file (UTF-8), overwriting if exists. */
    private void writeStringToFile(@NonNull File f, @NonNull String data) throws IOException {
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(data.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }

    /**
     * Async fetch. Always returns cached data first (if any), then attempts a remote refresh
     * if cache is stale or missing. Never blocks the calling thread.
     */
    public void fetch(@NonNull Callback cb) {
        io.execute(() -> {
            ChannelListResponse cached = loadCached();
            if (cached != null) {
                postOnMain(() -> cb.onDataLoaded(cached, true));
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L);
            boolean stale = (System.currentTimeMillis() - lastFetch) > CACHE_TTL_MS;
            if (cached == null || stale) {
                refreshAsync(cb);
            }
        });
    }

    /** Force a refresh regardless of cache age. */
    public void refresh(@NonNull Callback cb) {
        refreshAsync(cb);
    }

    private void refreshAsync(@NonNull Callback cb) {
        io.execute(() -> {
            try {
                Request req = new Request.Builder()
                        .url(remoteUrl)
                        .header("User-Agent", "FameTV/1.0 (Android TV)")
                        .header("Cache-Control", "max-stale=0")
                        .get()
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    if (!resp.isSuccessful()) {
                        throw new IOException("HTTP " + resp.code());
                    }
                    String body = resp.body() != null ? resp.body().string() : "";
                    ChannelListResponse parsed = gson.fromJson(body, ChannelListResponse.class);
                    if (parsed == null || parsed.getChannels() == null) {
                        throw new IOException("Empty or malformed channels.json");
                    }
                    // Write to cache file atomically
                    File f = cacheFile();
                    File tmp = new File(f.getAbsolutePath() + ".tmp");
                    writeStringToFile(tmp, body);
                    if (f.exists()) f.delete();
                    tmp.renameTo(f);

                    SharedPreferences.Editor ed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                    ed.putLong(KEY_LAST_FETCH, System.currentTimeMillis());
                    ed.putInt(KEY_LAST_VERSION, parsed.getVersion());
                    ed.apply();

                    postOnMain(() -> cb.onDataLoaded(parsed, false));
                }
            } catch (Exception e) {
                Log.e(TAG, "Refresh failed: " + e.getMessage());
                String msg = e.getMessage() == null ? "unknown" : e.getMessage();
                ChannelListResponse cached = loadCached();
                postOnMain(() -> cb.onError(msg, cached));
            }
        });
    }

    private void postOnMain(@NonNull Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
}
