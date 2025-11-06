package com.example.eventease.model;

import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Event {
    public String id;
    public String title;
    public long startsAtEpochMs;    // millis UTC
    public String location;
    public int capacity;
    @Nullable public String notes;
    @Nullable public String posterUrl;
    public String organizerId;
    public long createdAtEpochMs;
    @Nullable public String qrPayload; // string we encode in QR (e.g., "event:<id>")

    public Event() { /* for Firestore */ }

    public static Event newDraft(String organizerId) {
        Event e = new Event();
        e.id = UUID.randomUUID().toString();
        e.organizerId = organizerId;
        e.createdAtEpochMs = System.currentTimeMillis();
        return e;
    }

    public Map<String,Object> toMap() {
        Map<String,Object> m = new HashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("startsAtEpochMs", startsAtEpochMs);
        m.put("location", location);
        m.put("capacity", capacity);
        m.put("notes", notes);
        m.put("posterUrl", posterUrl);
        m.put("organizerId", organizerId);
        m.put("createdAtEpochMs", createdAtEpochMs);
        m.put("qrPayload", qrPayload);
        return m;
    }
}