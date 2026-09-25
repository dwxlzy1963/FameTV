package com.fametv.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
 * Channel list adapter that supports expandable country groups.
 *
 * Layout strategy: every item in the flat list is either a "Country header"
 * (clickable, expand/collapse) or a "Channel" item under that country. The
 * adapter handles focusability so D-pad navigation up/down between rows works
 * naturally — focus lands on the next visible item.
 */
public class CountryGroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** Flat item: either a country group header or a channel under a group. */
    public static final class FlatItem {
        static final int TYPE_HEADER = 1;
        static final int TYPE_CHANNEL = 2;

        final int type;
        final String countryCode;
        final String countryName;
        final int channelCount;
        final boolean expanded;
        final Channel channel;   // null for headers

        FlatItem(int type, String countryCode, String countryName, int channelCount,
                 boolean expanded, Channel channel) {
            this.type = type;
            this.countryCode = countryCode;
            this.countryName = countryName;
            this.channelCount = channelCount;
            this.expanded = expanded;
            this.channel = channel;
        }

        static FlatItem header(String code, String name, int count, boolean expanded) {
            return new FlatItem(TYPE_HEADER, code, name, count, expanded, null);
        }

        static FlatItem channel(Channel c) {
            return new FlatItem(TYPE_CHANNEL, c.getCountry(), c.getCountryName(), 0, false, c);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FlatItem)) return false;
            FlatItem f = (FlatItem) o;
            if (type != f.type) return false;
            if (type == TYPE_HEADER) {
                return countryCode != null && countryCode.equals(f.countryCode);
            }
            return channel != null && channel.getId() != null
                    && channel.getId().equals(f.channel != null ? f.channel.getId() : null);
        }

        @Override
        public int hashCode() {
            int r = type;
            if (countryCode != null) r = 31 * r + countryCode.hashCode();
            if (channel != null && channel.getId() != null) r = 31 * r + channel.getId().hashCode();
            return r;
        }
    }

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    public interface OnChannelLongClickListener {
        void onChannelLongClick(Channel channel);
    }

    public interface OnHeaderClickListener {
        void onHeaderClick(String countryCode);
    }

    private static final int TYPE_HEADER = FlatItem.TYPE_HEADER;
    private static final int TYPE_CHANNEL = FlatItem.TYPE_CHANNEL;

    private final List<FlatItem> items = new ArrayList<>();
    private final OnChannelClickListener channelClickListener;
    private final OnChannelLongClickListener longClickListener;
    private final OnHeaderClickListener headerClickListener;
    private final Set<String> favoriteIds = new HashSet<>();

    public CountryGroupAdapter(@NonNull OnChannelClickListener clickListener,
                               OnChannelLongClickListener longClickListener,
                               OnHeaderClickListener headerClickListener) {
        this.channelClickListener = clickListener;
        this.longClickListener = longClickListener;
        this.headerClickListener = headerClickListener;
        setHasStableIds(true);
    }

    public void setFavoriteIds(@NonNull Set<String> ids) {
        favoriteIds.clear();
        favoriteIds.addAll(ids);
        notifyDataSetChanged();
    }

    /** Submit the flat item list (already expanded/collapsed state baked in). */
    public void submitList(@NonNull List<FlatItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        FlatItem it = items.get(position);
        return it.hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @Override
    @NonNull
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inf.inflate(R.layout.item_country_header, parent, false);
            return new HeaderVH(v);
        }
        View v = inf.inflate(R.layout.item_channel, parent, false);
        return new ChannelVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        FlatItem item = items.get(position);
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).bind(item);
        } else if (holder instanceof ChannelVH) {
            ((ChannelVH) holder).bind(item.channel);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ------------------ View holders ------------------

    class HeaderVH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView count;
        final ImageView expandIcon;

        HeaderVH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.country_name);
            count = v.findViewById(R.id.country_count);
            expandIcon = v.findViewById(R.id.expand_icon);
            v.setOnClickListener(view -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && headerClickListener != null) {
                    FlatItem it = items.get(pos);
                    headerClickListener.onHeaderClick(it.countryCode);
                }
            });
            v.setOnLongClickListener(view -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && headerClickListener != null) {
                    FlatItem it = items.get(pos);
                    headerClickListener.onHeaderClick(it.countryCode);
                    return true;
                }
                return false;
            });
        }

        void bind(FlatItem item) {
            name.setText(item.countryName);
            count.setText(String.format("%d", item.channelCount));
            expandIcon.setRotation(item.expanded ? 90 : 0);
            itemView.setContentDescription(item.countryName + ", " + item.channelCount + " channels");
        }
    }

    class ChannelVH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView subtitle;
        final TextView badge;

        ChannelVH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.channel_name);
            subtitle = v.findViewById(R.id.channel_subtitle);
            badge = v.findViewById(R.id.channel_badge);

            v.setOnClickListener(view -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    FlatItem it = items.get(pos);
                    if (it.channel != null) channelClickListener.onChannelClick(it.channel);
                }
            });
            v.setOnLongClickListener(view -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && longClickListener != null) {
                    FlatItem it = items.get(pos);
                    if (it.channel != null) {
                        longClickListener.onChannelLongClick(it.channel);
                        return true;
                    }
                }
                return false;
            });
        }

        void bind(Channel ch) {
            name.setText(ch.getName());
            subtitle.setText(ch.getDisplaySubtitle());
            boolean fav = favoriteIds.contains(ch.getId());
            badge.setVisibility(fav ? View.VISIBLE : View.GONE);
            if (fav) badge.setText("\u2605");
        }
    }
}
