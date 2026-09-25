package com.fametv.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fametv.app.network.ChannelRepository;

/** Minimal settings screen — lets the user edit the remote channels.json URL. */
public class SettingsActivity extends AppCompatActivity {

    private TextView urlView;
    private TextView cacheInfoView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings);

        urlView = findViewById(R.id.settings_url);
        cacheInfoView = findViewById(R.id.settings_cache_info);

        findViewById(R.id.btn_edit_url).setOnClickListener(v -> promptEditUrl());
        findViewById(R.id.btn_refresh_now).setOnClickListener(v -> forceRefresh());
        findViewById(R.id.btn_clear_history).setOnClickListener(v -> {
            com.fametv.app.storage.Prefs.get(this).clearHistory();
            showToast(getString(R.string.history_cleared));
        });

        refreshViews();
    }

    private void refreshViews() {
        ChannelRepository repo = ChannelRepository.get(this);
        urlView.setText(repo.getRemoteUrl());

        com.fametv.app.model.ChannelListResponse cached = repo.loadCached();
        if (cached == null) {
            cacheInfoView.setText(R.string.no_cache);
        } else {
            cacheInfoView.setText(getString(R.string.cache_summary,
                    cached.getChannelCount(),
                    cached.getCountryCount(),
                    cached.getGeneratedAt() != null ? cached.getGeneratedAt() : "—"));
        }
    }

    private void promptEditUrl() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(ChannelRepository.get(this).getRemoteUrl());
        input.setSelection(input.getText().length());
        LinearLayout container = new LinearLayout(this);
        container.setPadding(48, 24, 48, 24);
        container.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(R.string.remote_url)
                .setView(container)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        ChannelRepository.get(this).setRemoteUrl(url);
                        showToast(getString(R.string.url_saved));
                        refreshViews();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void forceRefresh() {
        showToast(getString(R.string.refreshing));
        ChannelRepository.get(this).refresh(new ChannelRepository.Callback() {
            @Override
            public void onDataLoaded(com.fametv.app.model.ChannelListResponse response, boolean fromCache) {
                runOnUiThread(() -> {
                    showToast(getString(R.string.refreshed, response.getChannelCount()));
                    refreshViews();
                });
            }

            @Override
            public void onError(@androidx.annotation.NonNull String message,
                                com.fametv.app.model.ChannelListResponse cached) {
                runOnUiThread(() -> {
                    showToast(getString(R.string.refresh_failed, message));
                    refreshViews();
                });
            }
        });
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshViews();
    }
}
