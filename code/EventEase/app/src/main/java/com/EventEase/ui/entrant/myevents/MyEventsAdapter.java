package com.EventEase.ui.entrant.myevents;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.EventEase.model.Event;
import com.bumptech.glide.Glide;
import com.EventEase.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class MyEventsAdapter extends RecyclerView.Adapter<MyEventsAdapter.VH> {

    private final List<Event> data = new ArrayList<>();
    private final SimpleDateFormat df = new SimpleDateFormat("MMM d, h:mma", Locale.getDefault());
    private Set<String> invitedEventIds;

    private static final int[] ACCENT_COLORS = {
            Color.parseColor("#7FE8F5"),
            Color.parseColor("#F8B3FF"),
            Color.parseColor("#FFD66B"),
            Color.parseColor("#9BE7FF"),
            Color.parseColor("#FF9E9D")
    };

    void submit(List<Event> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    void setInvitedEventIds(Set<String> ids) {
        invitedEventIds = ids;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.entrant_item_event_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Event e = data.get(pos);

        // Title
        String titleText = e.getTitle();
        if (TextUtils.isEmpty(titleText)) {
            titleText = h.itemView.getContext().getString(R.string.event_details_title_placeholder);
        }
        h.title.setText(titleText);

        // Subtitle = date • location
        boolean hasLocation = !TextUtils.isEmpty(e.getLocation());
        String locationText = hasLocation
                ? e.getLocation()
                : h.itemView.getContext().getString(R.string.event_details_location_tbd);

        boolean hasDate = e.getStartsAtEpochMs() > 0;
        String dateText = hasDate
                ? df.format(new Date(e.getStartsAtEpochMs()))
                : h.itemView.getContext().getString(R.string.event_details_date_tbd);

        String meta;
        if (hasDate && hasLocation) {
            meta = dateText + " • " + e.getLocation();
        } else if (hasDate) {
            meta = dateText;
        } else if (hasLocation) {
            meta = e.getLocation();
        } else {
            meta = h.itemView.getContext().getString(R.string.discover_event_meta_placeholder);
        }
        h.subtitle.setText(meta);

        // Load event image with Glide
        Glide.with(h.image.getContext())
                .load(e.getPosterUrl())
                .placeholder(R.drawable.entrant_image_placeholder_event)
                .error(R.drawable.entrant_image_placeholder_event)
                .centerCrop()
                .into(h.image);

        // Accent dot - use special color if invited
        if (h.accentDot != null) {
            boolean invited = invitedEventIds != null && invitedEventIds.contains(e.getId());
            Drawable bg = h.accentDot.getBackground();
            if (bg != null) {
                if (invited) {
                    // Use a special color for invited events (gold/yellow)
                    DrawableCompat.setTint(bg.mutate(), Color.parseColor("#FFD700"));
                } else {
                    // Use normal accent color based on title
                    int colorIndex = Math.abs((e.getTitle() != null ? e.getTitle() : e.getId() != null ? e.getId() : "")
                            .hashCode()) % ACCENT_COLORS.length;
                    DrawableCompat.setTint(bg.mutate(), ACCENT_COLORS[colorIndex]);
                }
            }
        }
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title;
        final TextView subtitle;
        final View accentDot;
        
        VH(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.ivPoster);
            title = v.findViewById(R.id.tvTitle);
            subtitle = v.findViewById(R.id.tvMeta);
            accentDot = v.findViewById(R.id.eventAccent);
        }
    }
}
