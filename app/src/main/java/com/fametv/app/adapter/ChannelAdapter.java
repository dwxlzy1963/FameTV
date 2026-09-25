package com.fametv.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fametv.app.R;
import com.fametv.app.model.Channel;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid adapter for the channel list. Designed for TV D-pad navigation:
 * each item is focusable and shows a clear focus ring.
 */
public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    public interface OnChannelLongClickListener {
        void onChannelLongClick(Channel channel);
    }

    private final List<Channel> items = new ArrayList<>();
    private final OnChannelClickListener clickListener;
    private final OnChannelLongClickListener longClickListener;
    private final boolean showFavoriteBadge;
    private final java.util.Set<String> favoriteIds = new java.util.HashSet<>();

    public ChannelAdapter(@NonNull OnChannelClickListener clickListener,
                          OnChannelLongClickListener longClickListener,
                          boolean showFavoriteBadge) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        this.showFavoriteBadge = showFavoriteBadge;
    }

    public void submitList(@NonNull List<Channel> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void setFavoriteIds(@NonNull java.util.Set<String> ids) {
        favoriteIds.clear();
        favoriteIds.addAll(ids);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_channel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Channel ch = items.get(position);
        holder.bind(ch);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView subtitle;
        final TextView badge;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.channel_name);
            subtitle = v.findViewById(R.id.channel_subtitle);
            badge = v.findViewById(R.id.channel_badge);

            v.setOnClickListener(view -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    clickListener.onChannelClick(items.get(pos));
                }
            });
            v.setOnLongClickListener(view -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && longClickListener != null) {
                    longClickListener.onChannelLongClick(items.get(pos));
                    return true;
                }
                return false;
            });
        }

        void bind(Channel ch) {
            name.setText(ch.getName());
            subtitle.setText(ch.getDisplaySubtitle());
            boolean fav = showFavoriteBadge && favoriteIds.contains(ch.getId());
            badge.setVisibility(fav ? View.VISIBLE : View.GONE);
            if (fav) badge.setText("\u2605");  // ★
        }
    }
}
