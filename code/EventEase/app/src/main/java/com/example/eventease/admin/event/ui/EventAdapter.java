// File: EventAdapter.java
package com.example.eventease.admin.event.ui;

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
import java.util.function.Consumer;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private final LayoutInflater inflater;
    private final List<Event> items = new ArrayList<>();
    @Nullable private OnEventClickListener onEventClickListener;
    @NonNull private final Consumer<Event> onDeleteCallback;

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
        View view = inflater.inflate(R.layout.item_event_card, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = items.get(position);

        holder.tvTitle.setText(event.getTitle());

        String url = event.getPosterUrl();
        if (TextUtils.isEmpty(url)) {
            holder.ivPoster.setImageDrawable(new ColorDrawable(Color.parseColor("#546E7A")));
        } else {
            Glide.with(holder.ivPoster.getContext())
                    .load(url)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.ivPoster);
        }

        // Pass the callback forward to EventDetailActivity
        holder.itemView.setOnClickListener(v -> {
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
                // Try to show error to user if possible
                if (context instanceof android.app.Activity) {
                    android.widget.Toast.makeText(context, "Unable to open event details", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
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
