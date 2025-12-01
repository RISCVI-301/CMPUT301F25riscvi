package com.example.eventease.ui.organizer;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.eventease.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrganizerMyEventAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface OnClick { void onClick(String eventId); }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_EVENT = 1;

    private final List<EventListItem> items = new ArrayList<>();
    private final OnClick onClick;
    private final Context ctx;

    public OrganizerMyEventAdapter(Context ctx, OnClick onClick) {
        this.ctx = ctx;
        this.onClick = onClick;
    }

    public void setData(List<Map<String, Object>> list) {
        items.clear();
        if (list != null) {
            for (Map<String, Object> event : list) {
                items.add(EventListItem.createEvent(event));
            }
        }
        notifyDataSetChanged();
    }

    public void setSectionedData(List<EventListItem> sectionedList) {
        items.clear();
        if (sectionedList != null) {
            items.addAll(sectionedList);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_EVENT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.entrant_item_notification_header, parent, false);
            return new HeaderVH(v);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.organizer_my_events_item, parent, false);
            return new EventVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        EventListItem item = items.get(pos);
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).bind(item.headerText);
        } else if (holder instanceof EventVH) {
            ((EventVH) holder).bind(item.event, onClick);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        private final TextView headerText;

        HeaderVH(@NonNull View v) {
            super(v);
            headerText = v.findViewById(R.id.sectionHeader);
        }

        void bind(String text) {
            if (headerText != null) {
                headerText.setText(text);
            }
        }
    }

    static class EventVH extends RecyclerView.ViewHolder {
        ImageView imgPoster;
        TextView tvTitle, tvMeta;
        Context ctx;

        EventVH(@NonNull View v) {
            super(v);
            ctx = v.getContext();
            imgPoster = v.findViewById(R.id.imgPoster);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvMeta = v.findViewById(R.id.tvMeta);
        }

        void bind(Map<String, Object> m, OnClick onClick) {
            String title = asString(m.get("title"));
            String posterUrl = asString(m.get("posterUrl"));
            String eventId = asString(m.get("id"));
            String location = asString(m.get("location"));

            long start = asLong(m.get("registrationStart"));
            long end = asLong(m.get("registrationEnd"));
            long deadline = asLong(m.get("deadlineEpochMs"));
            int cap = asInt(m.get("capacity"), -1);

            tvTitle.setText(title != null && !title.isEmpty() ? title : "Untitled");

            String startTxt = start > 0 ? DateFormat.format("EEE, MMM d · h:mm a", start).toString() : "";
            String endTxt = end > 0 ? DateFormat.format("EEE, MMM d · h:mm a", end).toString() : "";
            String deadlineTxt = deadline > 0 ? DateFormat.format("EEE, MMM d · h:mm a", deadline).toString() : "";
            String capTxt = cap <= 0 ? "Any" : String.valueOf(cap);
            String meta = String.format(Locale.getDefault(), "%s  —  %s\ncap: %s%s",
                    startTxt, endTxt, capTxt,
                    (location != null && !location.isEmpty() ? "  ·  " + location : ""));
            
            // Add deadline if available
            if (deadline > 0) {
                meta += "\nDeadline: " + deadlineTxt;
            }
            
            tvMeta.setText(meta.trim());

            Glide.with(ctx)
                    .load(posterUrl)
                    .placeholder(android.R.color.darker_gray)
                    .error(android.R.color.darker_gray)
                    .centerCrop()
                    .into(imgPoster);

            itemView.setOnClickListener(v -> {
                if (eventId != null && !eventId.isEmpty()) onClick.onClick(eventId);
            });
        }

        private static String asString(Object o) {
            return o instanceof String ? (String) o : null;
        }

        private static long asLong(Object o) {
            return (o instanceof Number) ? ((Number) o).longValue() : 0L;
        }

        private static int asInt(Object o, int def) {
            return (o instanceof Number) ? ((Number) o).intValue() : def;
        }
    }

    public static class EventListItem {
        public boolean isHeader;
        public String headerText;
        public Map<String, Object> event;

        public static EventListItem createHeader(String text) {
            EventListItem item = new EventListItem();
            item.isHeader = true;
            item.headerText = text;
            return item;
        }

        public static EventListItem createEvent(Map<String, Object> event) {
            EventListItem item = new EventListItem();
            item.isHeader = false;
            item.event = event;
            return item;
        }
    }
}