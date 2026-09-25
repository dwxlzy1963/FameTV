package com.fametv.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fametv.app.adapter.ChannelAdapter;
import com.fametv.app.model.Channel;
import com.fametv.app.storage.Prefs;
import com.fametv.app.ui.GridSpacingItemDecoration;
import com.fametv.app.util.UiUtil;

import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ChannelAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        setTitle(R.string.history);
        recycler = findViewById(R.id.recycler);
        int spanCount = 6;
        recycler.setLayoutManager(new GridLayoutManager(this, spanCount));
        recycler.addItemDecoration(new GridSpacingItemDecoration(spanCount, UiUtil.dpToPx(this, 10), true));
        adapter = new ChannelAdapter(this::openChannel, this::onLongClick, false);
        recycler.setAdapter(adapter);
    }

    private void openChannel(Channel channel) {
        Prefs.get(this).addToHistory(channel);
        Intent intent = new Intent(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channel.getId())
                .putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, channel.getName())
                .putExtra(PlayerActivity.EXTRA_CHANNEL_URL, channel.getUrl())
                .putExtra(PlayerActivity.EXTRA_STREAM_URL, channel.getStreamUrl())
                .putExtra(PlayerActivity.EXTRA_YOUTUBE_URL, channel.getYoutubeUrl());
        startActivity(intent);
    }

    private void onLongClick(Channel channel) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_history)
                .setMessage(R.string.clear_history_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Prefs.get(this).clearHistory();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refresh() {
        List<Channel> list = Prefs.get(this).getHistory();
        adapter.submitList(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
