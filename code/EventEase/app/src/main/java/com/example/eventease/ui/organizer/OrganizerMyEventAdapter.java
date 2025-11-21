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

public class OrganizerMyEventAdapter extends RecyclerView.Adapter<OrganizerMyEventAdapter.VH> {
    public interface OnClick { void onClick(String eventId); }

    private final List<Map<String, Object>> items = new ArrayList<>();
    private final OnClick onClick;
    private final Context ctx;

    public OrganizerMyEventAdapter(Context ctx, OnClick onClick) {
        this.ctx = ctx;
        this.onClick = onClick;
    }

    public void setData(List<Map<String, Object>> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.organizer_my_events_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Map<String, Object> m = items.get(pos);
        String title = asString(m.get("title"));
        String posterUrl = asString(m.get("posterUrl"));
        String eventId = asString(m.get("id"));
        String location = asString(m.get("location"));

        long start = asLong(m.get("registrationStart"));
        long end = asLong(m.get("registrationEnd"));
        long deadline = asLong(m.get("deadlineEpochMs"));
        int cap = asInt(m.get("capacity"), -1);

        h.tvTitle.setText(title != null && !title.isEmpty() ? title : "Untitled");

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
        
        h.tvMeta.setText(meta.trim());

        Glide.with(ctx)
                .load(posterUrl)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .centerCrop()
                .into(h.imgPoster);

        h.itemView.setOnClickListener(v -> {
            if (eventId != null && !eventId.isEmpty()) onClick.onClick(eventId);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgPoster;
        TextView tvTitle, tvMeta;

        VH(@NonNull View v) {
            super(v);
            imgPoster = v.findViewById(R.id.imgPoster);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvMeta = v.findViewById(R.id.tvMeta);
        }
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