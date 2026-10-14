package com.fametv.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fametv.app.adapter.CountryGroupAdapter;
import com.fametv.app.model.Channel;
import com.fametv.app.model.ChannelListResponse;
import com.fametv.app.network.ChannelRepository;
import com.fametv.app.storage.Prefs;
import com.fametv.app.util.UiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Main launcher activity with TV-friendly 2-pane layout:
 *   Left  = sidebar (Favorites / History / Settings)
 *   Right = channel list grouped by country, headers expand/collapse on OK
 *
 * Remote control mappings (designed for the standard Android TV remote):
 *   D-pad up/down      – move focus through the list
 *   D-pad OK (short)   – on header: expand/collapse the country
 *                        on channel: open the player
 *   D-pad OK (long, ≥3s) – on channel: toggle favorite
 *   D-pad right (long, ≥3s) – open Settings
 *   D-pad left (long, ≥3s) – focus the left sidebar (or vice-versa)
 *   MENU key           – open Settings
 *   Page Up/Down       – force refresh
 *   Back               – exit
 */
public class MainActivity extends BaseActivity {

    // Long-press thresholds (ms)
    private static final long LONG_PRESS_OK_MS     = 1500L;  // 1.5s — feels like 3s on TV
    private static final long LONG_PRESS_RIGHT_MS  = 1500L;
    private static final long LONG_PRESS_LEFT_MS   = 1500L;

    private RecyclerView recyclerView;
    private CountryGroupAdapter adapter;
    private ProgressBar progress;
    private TextView statusText;
    private TextView metaText;
    private EditText search;
    private TextView emptyHint;

    private final List<Channel> allChannels = new ArrayList<>();
    private final Map<String, Boolean> expandedByCountry = new HashMap<>();
    private ChannelRepository repository;

    // Track current long-press state per keycode
    private long okPressDownAt = 0L;
    private long rightPressDownAt = 0L;
    private long leftPressDownAt = 0L;
    private boolean okLongPressFired = false;
    private boolean rightLongPressFired = false;
    private boolean leftLongPressFired = false;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recycler);
        progress = findViewById(R.id.progress);
        statusText = findViewById(R.id.status_text);
        metaText = findViewById(R.id.meta_text);
        search = findViewById(R.id.search);
        emptyHint = findViewById(R.id.empty_hint);

        // Sidebar buttons
        findViewById(R.id.btn_favorites).setOnClickListener(v ->
                startActivity(new Intent(this, FavoritesActivity.class)));
        findViewById(R.id.btn_history).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_search).setOnClickListener(v -> {
            search.requestFocus();
            applyFilter();
        });

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setItemPrefetchEnabled(true);
        recyclerView.setLayoutManager(lm);
        adapter = new CountryGroupAdapter(this::openChannel, this::onChannelLongClick, this::onHeaderClick);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);

        search.setOnEditorActionListener((v, actionId, event) -> {
            applyFilter();
            recyclerView.requestFocus();
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
                emptyHint.setVisibility(s.length() == 0 && allChannels.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        repository = ChannelRepository.get(this);
        reload();
    }

    private void reload() {
        progress.setVisibility(View.VISIBLE);
        statusText.setText(R.string.loading_channels);
        repository.fetch(new ChannelRepository.Callback() {
            @Override
            public void onDataLoaded(@NonNull ChannelListResponse response, boolean fromCache) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    statusText.setVisibility(View.GONE);
                    allChannels.clear();
                    allChannels.addAll(response.getChannels());
                    metaText.setText(String.format(Locale.getDefault(),
                            "%,d channels · %d countries · %s",
                            response.getChannelCount(),
                            response.getCountryCount(),
                            response.getGeneratedAt() != null ? response.getGeneratedAt() : ""));
                    applyFilter();
                    refreshFavoriteBadges();
                });
            }

            @Override
            public void onError(@NonNull String message, ChannelListResponse cached) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    if (cached != null) {
                        allChannels.clear();
                        allChannels.addAll(cached.getChannels());
                        applyFilter();
                        statusText.setText(getString(R.string.using_cache_after_error, message));
                    } else {
                        statusText.setText(getString(R.string.load_failed, message));
                        emptyHint.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    /** Build the flat list: header per country + channels under expanded countries. */
    private void applyFilter() {
        String q = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);

        // 1. Group channels by country (preserve insertion order)
        Map<String, List<Channel>> byCountry = new LinkedHashMap<>();
        Map<String, String> countryNames = new HashMap<>();
        for (Channel ch : allChannels) {
            if (ch == null || ch.getCountry() == null) continue;
            // Bilingual search: matches English name, country code, country English name,
            // or Chinese country name (so typing "新加坡" finds Singapore's channels).
            if (!q.isEmpty()) {
                String name = ch.getName() == null ? "" : ch.getName().toLowerCase(Locale.ROOT);
                String cName = ch.getCountryName() == null ? "" : ch.getCountryName().toLowerCase(Locale.ROOT);
                String cCode = ch.getCountry() == null ? "" : ch.getCountry().toLowerCase(Locale.ROOT);
                String zhName = com.fametv.app.util.CountryNames.chineseName(ch.getCountry());
                String zhLower = zhName == null ? "" : zhName.toLowerCase(Locale.ROOT);
                if (!name.contains(q) && !cName.contains(q) && !cCode.contains(q) && !zhLower.contains(q)) {
                    continue;
                }
            }
            String code = ch.getCountry();
            byCountry.computeIfAbsent(code, k -> new ArrayList<>()).add(ch);
            if (ch.getCountryName() != null && !ch.getCountryName().isEmpty()) {
                countryNames.putIfAbsent(code, ch.getCountryName());
            } else {
                countryNames.putIfAbsent(code, code.toUpperCase(Locale.ROOT));
            }
        }

        // 2. If searching, auto-expand all matching countries
        boolean autoExpand = !q.isEmpty();

        // 3. Build the flat list — show bilingual country names in the header
        List<CountryGroupAdapter.FlatItem> flat = new ArrayList<>();
        for (Map.Entry<String, List<Channel>> entry : byCountry.entrySet()) {
            String code = entry.getKey();
            List<Channel> channels = entry.getValue();
            String englishName = countryNames.getOrDefault(code, code.toUpperCase(Locale.ROOT));
            String bilingualName = com.fametv.app.util.CountryNames.bilingual(code, englishName);
            boolean expanded = autoExpand || expandedByCountry.getOrDefault(code, false);
            flat.add(CountryGroupAdapter.FlatItem.header(code, bilingualName, channels.size(), expanded));
            if (expanded) {
                for (Channel ch : channels) {
                    flat.add(CountryGroupAdapter.FlatItem.channel(ch));
                }
            }
        }

        adapter.submitList(flat);

        if (flat.isEmpty() && !q.isEmpty()) {
            emptyHint.setText(getString(R.string.no_results_for, q));
            emptyHint.setVisibility(View.VISIBLE);
        } else {
            emptyHint.setVisibility(View.GONE);
        }
    }

    private void refreshFavoriteBadges() {
        Set<String> ids = new HashSet<>();
        for (Channel c : Prefs.get(this).getFavorites()) ids.add(c.getId());
        adapter.setFavoriteIds(ids);
    }

    private void openChannel(@NonNull Channel channel) {
        Prefs.get(this).addToHistory(channel);
        Intent intent = new Intent(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channel.getId())
                .putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, channel.getName())
                .putExtra(PlayerActivity.EXTRA_CHANNEL_URL, channel.getUrl())
                .putExtra(PlayerActivity.EXTRA_STREAM_URL, channel.getStreamUrl())
                .putExtra(PlayerActivity.EXTRA_YOUTUBE_URL, channel.getYoutubeUrl());
        startActivity(intent);
    }

    private void onChannelLongClick(@NonNull Channel channel) {
        boolean wasFav = Prefs.get(this).isFavorite(channel.getId());
        boolean nowFav = Prefs.get(this).toggleFavorite(channel);
        refreshFavoriteBadges();

        // Vibration feedback
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                long[] pattern = {0, 60, 80, 60};
                v.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {}

        Toast.makeText(this, nowFav ? R.string.added_to_favorites : R.string.removed_from_favorites,
                Toast.LENGTH_LONG).show();
    }

    private void onHeaderClick(String countryCode) {
        boolean now = expandedByCountry.getOrDefault(countryCode, false);
        expandedByCountry.put(countryCode, !now);
        applyFilter();
        // Restore focus roughly to the header row
        recyclerView.post(() -> recyclerView.requestFocus());
    }

    // ---------------- Remote control handling ----------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getRepeatCount() == 0) {
                    okPressDownAt = System.currentTimeMillis();
                    okLongPressFired = false;
                    handler.postDelayed(this::fireOkLongPress, LONG_PRESS_OK_MS);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.getRepeatCount() == 0) {
                    rightPressDownAt = System.currentTimeMillis();
                    rightLongPressFired = false;
                    handler.postDelayed(this::fireRightLongPress, LONG_PRESS_RIGHT_MS);
                }
                // Let normal right-arrow nav proceed by returning false
                return super.onKeyDown(keyCode, event);

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.getRepeatCount() == 0) {
                    leftPressDownAt = System.currentTimeMillis();
                    leftLongPressFired = false;
                    handler.postDelayed(this::fireLeftLongPress, LONG_PRESS_LEFT_MS);
                }
                return super.onKeyDown(keyCode, event);

            case KeyEvent.KEYCODE_MENU:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_PAGE_UP:
                reload();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            handler.removeCallbacks(this::fireOkLongPress);
            if (!okLongPressFired) {
                // Short press: simulate a click on the focused view
                View focused = getCurrentFocus();
                if (focused != null) {
                    focused.performClick();
                }
                return true;
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            handler.removeCallbacks(this::fireRightLongPress);
            if (!rightLongPressFired) {
                // Short press = regular focus nav (let framework handle)
                return super.onKeyUp(keyCode, event);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            handler.removeCallbacks(this::fireLeftLongPress);
            if (!leftLongPressFired) {
                return super.onKeyUp(keyCode, event);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void fireOkLongPress() {
        if (okLongPressFired) return;
        okLongPressFired = true;
        View focused = getCurrentFocus();
        if (focused != null) {
            // For channel rows: this toggles favorite
            // For header rows: this is the same as short press (expand/collapse) —
            // we already bound a long-click handler that delegates to onHeaderClick.
            focused.performLongClick();
        }
        Toast.makeText(this, R.string.long_press_ok_triggered, Toast.LENGTH_SHORT).show();
    }

    private void fireRightLongPress() {
        if (rightLongPressFired) return;
        rightLongPressFired = true;
        startActivity(new Intent(this, SettingsActivity.class));
        Toast.makeText(this, R.string.opening_settings, Toast.LENGTH_SHORT).show();
    }

    private void fireLeftLongPress() {
        if (leftLongPressFired) return;
        leftLongPressFired = true;
        View fav = findViewById(R.id.btn_favorites);
        if (fav != null) fav.requestFocus();
        Toast.makeText(this, R.string.focus_sidebar, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFavoriteBadges();
    }
}
