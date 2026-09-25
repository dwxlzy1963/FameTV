package com.fametv.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
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

import java.util.Locale;

/**
 * Player screen. Two playback modes:
 *   1) Native ExoPlayer when stream_url (m3u8) is available — best UX on TV.
 *   2) WebView fallback to famelack.com/tv/{country}/{id} when only YouTube or
 *      geo-blocked streams are present.
 *
 * TV remote mappings:
 *   - D-pad center: toggle play/pause
 *   - Up/Down: volume
 *   - Left/Right: seek 10s (ExoPlayer mode) or skip YouTube 5s (WebView mode)
 *   - MENU: open stream selector
 *   - BACK: exit
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_CHANNEL_ID = "channel_id";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    public static final String EXTRA_CHANNEL_URL = "channel_url";
    public static final String EXTRA_STREAM_URL = "stream_url";
    public static final String EXTRA_YOUTUBE_URL = "youtube_url";

    private static final String TAG = "PlayerActivity";

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

    private boolean chromeClientCustomView = false;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fullscreen immersive from the start
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

        channelId = getIntent().getStringExtra(EXTRA_CHANNEL_ID);
        channelName = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
        channelUrl = getIntent().getStringExtra(EXTRA_CHANNEL_URL);
        streamUrl = getIntent().getStringExtra(EXTRA_STREAM_URL);
        youtubeUrl = getIntent().getStringExtra(EXTRA_YOUTUBE_URL);

        titleText.setText(channelName);
        subtitleText.setText(channelUrl);

        findViewById(R.id.player_back).setOnClickListener(v -> finish());
        findViewById(R.id.player_menu).setOnClickListener(v -> showStreamSelector());

        startPlayback();
    }

    private void startPlayback() {
        // Prefer native ExoPlayer when a direct HLS stream is available — better
        // remote-control support than a WebView.
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

        exoPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .build();
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                runOnUiThread(() -> {
                    progress.setVisibility(state == Player.STATE_READY ? View.GONE : View.VISIBLE);
                    if (state == Player.STATE_READY) subtitleText.setText(channelUrl);
                });
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
        // Use the stock player view as a child (declared in layout)
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
                return false;  // load all URLs in-app
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

    /** Inject CSS / JS to nudge famelack.com's player toward fullscreen-friendly behavior. */
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

    // ---------------- Remote control ----------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
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
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_VOLUME_UP:
                adjustVolume(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                adjustVolume(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (exoPlayer != null) seekRelative(-10_000);
                else if (webView != null) webView.dispatchKeyEvent(event);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (exoPlayer != null) seekRelative(10_000);
                else if (webView != null) webView.dispatchKeyEvent(event);
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

    private void seekRelative(long ms) {
        if (exoPlayer != null) {
            long pos = Math.max(0, Math.min(exoPlayer.getDuration(), exoPlayer.getCurrentPosition() + ms));
            exoPlayer.seekTo(pos);
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
}
