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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Channel adapter that supports two row layouts:
 *  - LAYOUT_COMPACT:  one-line horizontal row (used in main country list)
 *  - LAYOUT_FULL:    two-line row that wraps long channel names (Favorites/History)
 *
 * Designed for TV D-pad navigation — each item is focusable with a clear focus ring.
 */
public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public static final int LAYOUT_COMPACT = 0;
    public static final int LAYOUT_FULL    = 1;

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
    private final int layoutMode;
    private final Set<String> favoriteIds = new HashSet<>();

    /** Default constructor uses the compact layout (backward compatible). */
    public ChannelAdapter(@NonNull OnChannelClickListener clickListener,
                          OnChannelLongClickListener longClickListener,
                          boolean showFavoriteBadge) {
        this(clickListener, longClickListener, showFavoriteBadge, LAYOUT_COMPACT);
    }

    public ChannelAdapter(@NonNull OnChannelClickListener clickListener,
                          OnChannelLongClickListener longClickListener,
                          boolean showFavoriteBadge,
                          int layoutMode) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        this.showFavoriteBadge = showFavoriteBadge;
        this.layoutMode = layoutMode;
    }

    public void submitList(@NonNull List<Channel> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void setFavoriteIds(@NonNull Set<String> ids) {
        favoriteIds.clear();
        favoriteIds.addAll(ids);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int res = layoutMode == LAYOUT_FULL ? R.layout.item_channel_full : R.layout.item_channel;
        View v = LayoutInflater.from(parent.getContext()).inflate(res, parent, false);
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
