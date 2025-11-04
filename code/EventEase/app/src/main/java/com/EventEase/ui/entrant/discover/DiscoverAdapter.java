package com.EventEase.ui.entrant.discover;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.EventEase.model.Event;
import com.bumptech.glide.Glide;
import com.example.eventease.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter showing discoverable events coming from Firestore.
 */
public class DiscoverAdapter extends RecyclerView.Adapter<DiscoverAdapter.VH> {

    public interface OnEventClickListener {
        void onEventClick(@NonNull Event event);
    }

    private final List<Event> items = new ArrayList<>();
    private final OnEventClickListener onClickListener;

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, h:mma", Locale.getDefault());

    public DiscoverAdapter(@NonNull OnEventClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    public void submit(@Nullable List<Event> events) {
        items.clear();
        if (events != null) {
            items.addAll(events);
        }
        notifyDataSetChanged();
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    private static final int[] ACCENT_COLORS = {
            Color.parseColor("#7FE8F5"),
            Color.parseColor("#F8B3FF"),
            Color.parseColor("#FFD66B"),
            Color.parseColor("#9BE7FF"),
            Color.parseColor("#FF9E9D")
    };

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvMeta;
        final ImageView ivPoster;
        final View accentDot;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            ivPoster = itemView.findViewById(R.id.ivPoster);
            accentDot = itemView.findViewById(R.id.eventAccent);
        }

        void bind(@NonNull Event event,
                  @NonNull OnEventClickListener onClickListener) {

            String titleText = event.getTitle();
            if (titleText == null || titleText.trim().isEmpty()) {
                titleText = itemView.getContext().getString(R.string.event_details_title_placeholder);
            }
            tvTitle.setText(titleText);

            boolean hasLocation = !TextUtils.isEmpty(event.getLocation());
            String locationText = hasLocation
                    ? event.getLocation()
                    : itemView.getContext().getString(R.string.event_details_location_tbd);

            boolean hasDate = event.getStartsAtEpochMs() > 0;
            String dateText = hasDate
                    ? DATE_FORMAT.format(new Date(event.getStartsAtEpochMs()))
                    : itemView.getContext().getString(R.string.event_details_date_tbd);

            String meta;
            if (hasDate && hasLocation) {
                meta = dateText + " • " + event.getLocation();
            } else if (hasDate) {
                meta = dateText;
            } else if (hasLocation) {
                meta = event.getLocation();
            } else {
                meta = itemView.getContext().getString(R.string.discover_event_meta_placeholder);
            }
            tvMeta.setText(meta);

            Glide.with(ivPoster.getContext())
                    .load(event.getPosterUrl())
                    .placeholder(R.drawable.image_placeholder_event)
                    .error(R.drawable.image_placeholder_event)
                    .centerCrop()
                    .into(ivPoster);

            if (accentDot != null) {
                Drawable bg = accentDot.getBackground();
                if (bg != null) {
                    int colorIndex = Math.abs((event.getTitle() != null ? event.getTitle() : event.getId() != null ? event.getId() : "")
                            .hashCode()) % ACCENT_COLORS.length;
                    DrawableCompat.setTint(bg.mutate(), ACCENT_COLORS[colorIndex]);
                }
            }

            itemView.setOnClickListener(v -> onClickListener.onEventClick(event));
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), onClickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
