package com.fametv.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.datasource.DefaultDataSource;

import com.fametv.app.model.Channel;
import com.fametv.app.model.ChannelListResponse;
import com.fametv.app.network.ChannelRepository;
import com.fametv.app.storage.Prefs;

import java.util.List;
import java.util.Locale;

/**
 * Player screen. Two playback modes:
 *   1) Native ExoPlayer when stream_url (m3u8) is available — best UX on TV.
 *   2) WebView fallback to famelack.com/tv/{country}/{id} when only YouTube or
 *      geo-blocked streams are present.
 *
 * TV remote mappings (standard TV-app behavior):
 *   - D-pad Up/Down : switch to previous / next channel in the catalog
 *   - D-pad Left/Right: volume down / volume up
 *   - D-pad OK (short) : play / pause
 *   - D-pad OK (long ≥1.5s): toggle favorite for the current channel
 *   - D-pad Right (long ≥1.5s): open Settings
 *   - MENU: open stream source selector
 *   - BACK: exit (or leave WebView custom-view fullscreen first)
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_CHANNEL_ID = "channel_id";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    public static final String EXTRA_CHANNEL_URL = "channel_url";
    public static final String EXTRA_STREAM_URL = "stream_url";
    public static final String EXTRA_YOUTUBE_URL = "youtube_url";

    private static final String TAG = "PlayerActivity";
    private static final long LONG_PRESS_MS = 1500L;

    private TextView titleText;
    private TextView subtitleText;
    private ProgressBar progress;
    private FrameLayout root;
    private View exoView;
    private WebView webView;

    @Nullable private ExoPlayer exoPlayer;
    private AudioManager audioManager;

    private String channelId;
    private String channelName;
    private String channelUrl;
    private String streamUrl;
    private String youtubeUrl;

    /** Catalog for channel up/down switching. */
    private List<Channel> catalog;
    private int currentIndex = -1;

    private boolean chromeClientCustomView = false;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    // Long-press state
    private boolean okLongFired = false;
    private boolean rightLongFired = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);

        root = findViewById(R.id.player_root);
        titleText = findViewById(R.id.player_title);
        subtitleText = findViewById(R.id.player_subtitle);
        progress = findViewById(R.id.player_progress);
        exoView = findViewById(R.id.exo_view);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        loadIntent(getIntent());
        loadCatalog();
        startPlayback();
    }

    private void loadIntent(@NonNull Intent intent) {
        channelId = intent.getStringExtra(EXTRA_CHANNEL_ID);
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME);
        channelUrl = intent.getStringExtra(EXTRA_CHANNEL_URL);
        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL);
        youtubeUrl = intent.getStringExtra(EXTRA_YOUTUBE_URL);
        titleText.setText(channelName);
        subtitleText.setText(channelUrl);
    }

    /** Pull the catalog from cache so we can switch channels on Up/Down keys. */
    private void loadCatalog() {
        ChannelListResponse cached = ChannelRepository.get(this).loadCached();
        if (cached == null || cached.getChannels() == null) return;
        catalog = cached.getChannels();
        for (int i = 0; i < catalog.size(); i++) {
            Channel c = catalog.get(i);
            if (c != null && c.getId() != null && c.getId().equals(channelId)) {
                currentIndex = i;
                return;
            }
        }
        currentIndex = -1;
    }

    private void startPlayback() {
        if (streamUrl != null && !streamUrl.isEmpty() && streamUrl.startsWith("http")) {
            launchExoPlayer(streamUrl);
        } else if (channelUrl != null && !channelUrl.isEmpty()) {
            launchWebView(channelUrl);
        } else if (youtubeUrl != null && !youtubeUrl.isEmpty()) {
            launchWebView(youtubeUrl);
        } else {
            Toast.makeText(this, R.string.no_playable_source, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // ---------------- ExoPlayer path ----------------

    private void launchExoPlayer(@NonNull String hlsUrl) {
        exoView.setVisibility(View.VISIBLE);
        if (webView != null) webView.setVisibility(View.GONE);

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        HlsMediaSource.Factory hlsFactory = new HlsMediaSource.Factory(dataSourceFactory);
        MediaSource mediaSource = hlsFactory.createMediaSource(MediaItem.fromUri(hlsUrl));

        releaseExoPlayer();
        exoPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .build();
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                runOnUiThread(() -> progress.setVisibility(state == Player.STATE_READY ? View.GONE : View.VISIBLE));
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                runOnUiThread(() -> {
                    Toast.makeText(PlayerActivity.this,
                            "Stream error: " + error.getMessage() + "\nSwitching to WebView…",
                            Toast.LENGTH_LONG).show();
                    releaseExoPlayer();
                    if (channelUrl != null) launchWebView(channelUrl);
                });
            }
        });
        exoPlayer.prepare();
        if (exoView instanceof androidx.media3.ui.PlayerView) {
            ((androidx.media3.ui.PlayerView) exoView).setPlayer(exoPlayer);
        }
    }

    private void releaseExoPlayer() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        if (exoView instanceof androidx.media3.ui.PlayerView) {
            ((androidx.media3.ui.PlayerView) exoView).setPlayer(null);
        }
    }

    // ---------------- WebView path ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private void launchWebView(@NonNull String url) {
        runOnUiThread(() -> {
            if (exoView != null) exoView.setVisibility(View.GONE);
            releaseExoPlayer();

            if (webView == null) {
                webView = new WebView(PlayerActivity.this);
                webView.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                root.addView(webView);
                configureWebView(webView);
            }
            webView.setVisibility(View.VISIBLE);
            progress.setVisibility(View.VISIBLE);
            subtitleText.setText(url);
            webView.loadUrl(url);
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(@NonNull WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " FameTV/1.0 (Android TV)");
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return false;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                progress.setVisibility(View.GONE);
                injectPlayerTweaks(v);
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                chromeClientCustomView = true;
                customView = view;
                customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                titleText.setVisibility(View.GONE);
                subtitleText.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                chromeClientCustomView = false;
                root.removeView(customView);
                customView = null;
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                titleText.setVisibility(View.VISIBLE);
                subtitleText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void injectPlayerTweaks(@NonNull WebView v) {
        String js = "(function(){"
                + "  try {"
                + "    var v = document.querySelector('video');"
                + "    if (v) { v.setAttribute('playsinline','true'); v.play && v.play(); }"
                + "    var style = document.createElement('style');"
                + "    style.innerHTML = 'body{background:#000!important;} header,nav,footer,[class*=ad-],[class*=banner]{display:none!important;}';"
                + "    document.head.appendChild(style);"
                + "  } catch(e) {}"
                + "})();";
        v.evaluateJavascript(js, null);
    }

    // ---------------- Channel switching ----------------

    private void switchChannel(int delta) {
        if (catalog == null || catalog.isEmpty()) return;
        if (currentIndex < 0) currentIndex = 0;
        int next = currentIndex + delta;
        if (next < 0) next = 0;
        if (next >= catalog.size()) next = catalog.size() - 1;
        if (next == currentIndex) return;
        Channel ch = catalog.get(next);
        if (ch == null) return;

        currentIndex = next;
        channelId = ch.getId();
        channelName = ch.getName();
        channelUrl = ch.getUrl();
        streamUrl = ch.getStreamUrl();
        youtubeUrl = ch.getYoutubeUrl();

        titleText.setText(channelName);
        subtitleText.setText(channelUrl);

        // Record history
        Prefs.get(this).addToHistory(ch);

        // Show a quick toast so the user knows which channel they're on
        Toast.makeText(this, ch.getName(), Toast.LENGTH_SHORT).show();

        startPlayback();
    }

    // ---------------- Remote control ----------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getRepeatCount() == 0) {
                    okLongFired = false;
                    handler.postDelayed(this::fireOkLong, LONG_PRESS_MS);
                }
                // Don't consume; PlayerView's controller will use it for play/pause
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                // Channel up
                switchChannel(-1);
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                // Channel down
                switchChannel(1);
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                adjustVolume(-1);
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.getRepeatCount() == 0) {
                    rightLongFired = false;
                    handler.postDelayed(this::fireRightLong, LONG_PRESS_MS);
                }
                adjustVolume(1);
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                togglePlayPause();
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (exoPlayer != null) exoPlayer.setPlayWhenReady(true);
                return true;

            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (exoPlayer != null) exoPlayer.setPlayWhenReady(false);
                return true;

            case KeyEvent.KEYCODE_MENU:
                showStreamSelector();
                return true;

            case KeyEvent.KEYCODE_BACK:
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
                if (chromeClientCustomView && customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            handler.removeCallbacks(this::fireOkLong);
            if (!okLongFired) {
                togglePlayPause();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            handler.removeCallbacks(this::fireRightLong);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void fireOkLong() {
        if (okLongFired) return;
        okLongFired = true;
        // Toggle favorite for the currently playing channel
        if (channelId == null) return;
        boolean fav = Prefs.get(this).isFavorite(channelId);
        // Build a minimal Channel object from the current playback state
        Channel ch = new Channel(channelId, channelName, null, null,
                null, false, channelUrl, streamUrl, youtubeUrl);
        Prefs.get(this).toggleFavorite(ch);
        Toast.makeText(this, fav ? R.string.removed_from_favorites : R.string.added_to_favorites,
                Toast.LENGTH_SHORT).show();
    }

    private void fireRightLong() {
        if (rightLongFired) return;
        rightLongFired = true;
        startActivity(new android.content.Intent(this, SettingsActivity.class));
    }

    private void togglePlayPause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(!exoPlayer.getPlayWhenReady());
        } else if (webView != null) {
            webView.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');"
                            + "if(v){if(v.paused){v.play()}else{v.pause()}}})();",
                    null);
        }
    }

    private void adjustVolume(int direction) {
        if (audioManager == null) return;
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int next = Math.max(0, Math.min(max, cur + direction));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
        Toast.makeText(this,
                String.format(Locale.getDefault(), "Volume: %d/%d", next, max),
                Toast.LENGTH_SHORT).show();
    }

    private void showStreamSelector() {
        String[] items = new String[]{
                getString(R.string.use_native_player) + (streamUrl != null ? " (m3u8)" : " (none)"),
                getString(R.string.use_webview) + " (famelack.com)",
                youtubeUrl != null ? getString(R.string.use_webview) + " (YouTube)" : "—"
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.stream_source)
                .setItems(items, (d, which) -> {
                    releaseExoPlayer();
                    if (webView != null) webView.setVisibility(View.GONE);
                    switch (which) {
                        case 0:
                            if (streamUrl != null) launchExoPlayer(streamUrl);
                            else Toast.makeText(this, R.string.no_m3u8, Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            if (channelUrl != null) launchWebView(channelUrl);
                            break;
                        case 2:
                            if (youtubeUrl != null) launchWebView(youtubeUrl);
                            break;
                    }
                })
                .show();
    }

    // ---------------- Immersive mode ----------------

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        int flags = View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        root.setSystemUiVisibility(flags);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        // Allow being re-launched with a new channel without recreating the activity
        loadIntent(intent);
        loadCatalog();
        startPlayback();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseExoPlayer();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) exoPlayer.setPlayWhenReady(false);
    }

    // --- Required imports to compile (alias usage so the imports above resolve) ---
    private static final String _TAG = TAG;
}
