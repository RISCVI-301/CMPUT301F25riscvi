package com.example.eventease.admin.event.ui;

import android.content.Intent;
import com.example.eventease.admin.event.ui.EventDetailActivity;


import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.eventease.R;
import com.example.eventease.admin.event.data.Event;

import java.util.ArrayList;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private final LayoutInflater inflater;
    private final List<Event> items = new ArrayList<>();
    @Nullable private OnEventClickListener onEventClickListener;

    public EventAdapter(@NonNull Context context, @NonNull List<Event> events) {
        this.inflater = LayoutInflater.from(context);
        if (events != null) this.items.addAll(events);
        setHasStableIds(true);
    }

    /** Optional click listener so each box can be clickable if you wire it later. */
    public interface OnEventClickListener {
        void onEventClick(@NonNull Event event, int position);
    }

    public void setOnEventClickListener(@Nullable OnEventClickListener listener) {
        this.onEventClickListener = listener;
    }

    /** Replace all items (simple version; swap to DiffUtil if needed). */
    public void submitList(@NonNull List<Event> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) {
        // If your Event has a real id, use it; otherwise fall back to position.
        return position;
    }

    @NonNull @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_event_card, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = items.get(position);

        // Title (bottom-left)
        holder.tvTitle.setText(event.getTitle());

        // Poster background
        String url = event.getPosterUrl();
        if (TextUtils.isEmpty(url)) {
            // Safe fallback color if no image available
            holder.ivPoster.setImageDrawable(new ColorDrawable(Color.parseColor("#546E7A")));
        } else {
            Glide.with(holder.ivPoster.getContext())
                    .load(url)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.ivPoster);
        }

        // Optional click handling
        holder.itemView.setOnClickListener(v -> {
            Intent i = new Intent(v.getContext(), EventDetailActivity.class);
            i.putExtra(EventDetailActivity.EXTRA_EVENT, event); // Event must implement Serializable
            // i.putExtra(EventDetailActivity.EXTRA_WAITLIST_COUNT, 0); // optional
            v.getContext().startActivity(i);
        });

    }

    @Override public int getItemCount() { return items.size(); }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPoster;
        TextView tvTitle;
        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPoster = itemView.findViewById(R.id.ivPoster);
            tvTitle  = itemView.findViewById(R.id.tvTitle);
        }
    }
}