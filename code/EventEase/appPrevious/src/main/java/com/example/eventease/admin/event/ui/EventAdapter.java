// File: EventAdapter.java
package com.example.eventease.admin.event.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.eventease.R;
import com.example.eventease.model.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private final LayoutInflater inflater;
    private final List<Event> items = new ArrayList<>();
    @Nullable private OnEventClickListener onEventClickListener;
    @NonNull private final Consumer<Event> onDeleteCallback;

    private static final int[] ACCENT_COLORS = {
            Color.parseColor("#7FE8F5"),
            Color.parseColor("#F8B3FF"),
            Color.parseColor("#FFD66B"),
            Color.parseColor("#9BE7FF"),
            Color.parseColor("#FF9E9D")
    };

    public EventAdapter(@NonNull Context context,
                        @NonNull List<Event> events,
                        @NonNull Consumer<Event> onDeleteCallback) {
        this.inflater = LayoutInflater.from(context);
        if (events != null) this.items.addAll(events);
        this.onDeleteCallback = onDeleteCallback;
        setHasStableIds(true);
    }

    public interface OnEventClickListener {
        void onEventClick(@NonNull Event event, int position);
    }

    public void setOnEventClickListener(@Nullable OnEventClickListener listener) {
        this.onEventClickListener = listener;
    }

    public void submitList(@NonNull List<Event> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** Remove by stable id and notify the exact removal index. */
    public boolean removeById(@NonNull String id) {
        for (int i = 0; i < items.size(); i++) {
            Event e = items.get(i);
            if (id.equals(e.getId())) {
                items.remove(i);
                notifyItemRemoved(i);
                return true;
            }
        }
        return false;
    }

    @Override public long getItemId(int position) {
        Event e = items.get(position);
        String id = e.getId();
        return (id != null) ? id.hashCode() : position;
    }

    @NonNull @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.entrant_item_event_card, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = items.get(position);
        holder.bind(event);
    }

    @Override public int getItemCount() { return items.size(); }

    class EventViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvMeta;
        final ImageView ivPoster;
        final View eventAccent;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            ivPoster = itemView.findViewById(R.id.ivPoster);
            eventAccent = itemView.findViewById(R.id.eventAccent);
        }

        void bind(@NonNull Event event) {
            String titleText = event.getTitle();
            if (titleText == null || titleText.trim().isEmpty()) {
                titleText = "Untitled Event";
            }
            tvTitle.setText(titleText);

            // Format date, time, and location for meta text (organizer style)
            boolean hasDate = event.getStartsAtEpochMs() > 0;
            String dateTimeText = hasDate
                    ? DateFormat.format("EEE, MMM d · h:mm a", event.getStartsAtEpochMs()).toString()
                    : "Date TBD";

            String locationText = event.getLocation();
            boolean hasLocation = !TextUtils.isEmpty(locationText);

            String meta;
            if (hasDate && hasLocation) {
                meta = dateTimeText + " • " + locationText;
            } else if (hasDate) {
                meta = dateTimeText;
            } else if (hasLocation) {
                meta = locationText;
            } else {
                meta = "Details TBD";
            }
            tvMeta.setText(meta);

            // Load poster image
            String url = event.getPosterUrl();
            if (TextUtils.isEmpty(url)) {
                Glide.with(ivPoster.getContext())
                        .load(R.drawable.entrant_image_placeholder_event)
                        .centerCrop()
                        .into(ivPoster);
            } else {
                Glide.with(ivPoster.getContext())
                        .load(url)
                        .placeholder(R.drawable.entrant_image_placeholder_event)
                        .error(R.drawable.entrant_image_placeholder_event)
                        .centerCrop()
                        .into(ivPoster);
            }

            // Set accent dot color
            if (eventAccent != null) {
                Drawable bg = eventAccent.getBackground();
                if (bg != null) {
                    int colorIndex = Math.abs((event.getTitle() != null ? event.getTitle() : event.getId() != null ? event.getId() : "")
                            .hashCode()) % ACCENT_COLORS.length;
                    DrawableCompat.setTint(bg.mutate(), ACCENT_COLORS[colorIndex]);
                }
            }

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (event == null) {
                    return;
                }
                
                Context context = v.getContext();
                if (context == null) {
                    return;
                }
                
                try {
                    EventDetailActivity.start(context, event, onDeleteCallback);
                } catch (Exception e) {
                    android.util.Log.e("EventAdapter", "Error starting EventDetailActivity: " + e.getMessage(), e);
                    if (context instanceof android.app.Activity) {
                        android.widget.Toast.makeText(context, "Unable to open event details", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
}
