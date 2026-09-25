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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.fametv.app.adapter.ChannelAdapter;
import com.fametv.app.model.Channel;
import com.fametv.app.model.ChannelListResponse;
import com.fametv.app.network.ChannelRepository;
import com.fametv.app.storage.Prefs;
import com.fametv.app.ui.GridSpacingItemDecoration;
import com.fametv.app.util.UiUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Main launcher activity. Renders a grid of channels with D-pad-friendly focus,
 * search box, and quick access to Favorites / History / Settings.
 */
public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChannelAdapter adapter;
    private ProgressBar progress;
    private TextView statusText;
    private TextView metaText;
    private EditText search;
    private SwipeRefreshLayout swipe;

    private final List<Channel> allChannels = new ArrayList<>();
    private ChannelRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recycler);
        progress = findViewById(R.id.progress);
        statusText = findViewById(R.id.status_text);
        metaText = findViewById(R.id.meta_text);
        search = findViewById(R.id.search);
        swipe = findViewById(R.id.swipe);

        findViewById(R.id.btn_favorites).setOnClickListener(v ->
                startActivity(new Intent(this, FavoritesActivity.class)));
        findViewById(R.id.btn_history).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        int spanCount = 6;
        GridLayoutManager lm = new GridLayoutManager(this, spanCount);
        recyclerView.setLayoutManager(lm);
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(spanCount, UiUtil.dpToPx(this, 10), true));
        adapter = new ChannelAdapter(this::openChannel, this::onChannelLongClick, true);
        recyclerView.setAdapter(adapter);

        swipe.setOnRefreshListener(this::reload);

        search.setOnEditorActionListener((v, actionId, event) -> {
            applyFilter();
            return false;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
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
                    swipe.setRefreshing(false);
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
                    swipe.setRefreshing(false);
                    if (cached != null) {
                        allChannels.clear();
                        allChannels.addAll(cached.getChannels());
                        applyFilter();
                        statusText.setText(getString(R.string.using_cache_after_error, message));
                    } else {
                        statusText.setText(getString(R.string.load_failed, message));
                    }
                });
            }
        });
    }

    private void applyFilter() {
        String q = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Channel> filtered = new ArrayList<>();
        for (Channel ch : allChannels) {
            if (q.isEmpty()
                    || (ch.getName() != null && ch.getName().toLowerCase(Locale.ROOT).contains(q))
                    || (ch.getCountryName() != null && ch.getCountryName().toLowerCase(Locale.ROOT).contains(q))
                    || (ch.getCountry() != null && ch.getCountry().toLowerCase(Locale.ROOT).contains(q))) {
                filtered.add(ch);
            }
        }
        adapter.submitList(filtered);
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
        boolean fav = Prefs.get(this).isFavorite(channel.getId());
        new AlertDialog.Builder(this)
                .setTitle(channel.getName())
                .setMessage(channel.getDisplaySubtitle() + "\n\n" + getString(R.string.channel_url) + ": " + channel.getUrl())
                .setPositiveButton(fav ? R.string.remove_favorite : R.string.add_favorite, (d, w) -> {
                    Prefs.get(this).toggleFavorite(channel);
                    refreshFavoriteBadges();
                })
                .setNegativeButton(R.string.play_now, (d, w) -> openChannel(channel))
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Allow MENU key on TV remote to open settings quickly
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        // Allow REFRESH (long-press center on some remotes) to reload
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            reload();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFavoriteBadges();
    }
}
