package com.EventEase.ui.entrant.myevents;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.EventEase.model.Event;
import com.example.eventease.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class MyEventsAdapter extends RecyclerView.Adapter<MyEventsAdapter.VH> {

    private final List<Event> data = new ArrayList<>();
    private final SimpleDateFormat df = new SimpleDateFormat("MMM d, h:mma", Locale.getDefault());
    private Set<String> invitedEventIds;

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
                .inflate(R.layout.item_my_event_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Event e = data.get(pos);

        // Title
        h.title.setText(e.getTitle() != null ? e.getTitle() : "Untitled");

        // Subtitle = date • location
        String when = e.getStartAt() != null ? df.format(e.getStartAt()) : "TBD";
        String where = e.getLocation() != null ? e.getLocation() : "";
        h.subtitle.setText(where.isEmpty() ? when : (when + " • " + where));

        // Image placeholder (replace with real image later if you have URLs)
        h.image.setImageResource(R.drawable.card_image_placeholder);

        // INVITED badge
        boolean invited = invitedEventIds != null && invitedEventIds.contains(e.getId());
        h.badge.setVisibility(invited ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;     // @id/event_image
        final TextView title;      // @id/event_name
        final TextView subtitle;   // @id/event_price
        final TextView badge;      // @id/invited_badge
        VH(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.event_image);
            title = v.findViewById(R.id.event_name);
            subtitle = v.findViewById(R.id.event_price);
            badge = v.findViewById(R.id.invited_badge);
        }
    }
}
