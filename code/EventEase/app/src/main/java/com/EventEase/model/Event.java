package com.EventEase.model;

import androidx.annotation.Nullable;
import java.util.Date;
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

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public long getStartsAtEpochMs() { return startsAtEpochMs; }
    public void setStartsAtEpochMs(long startsAtEpochMs) { this.startsAtEpochMs = startsAtEpochMs; }

    // Helper to get as Date for backward compatibility with existing code
    public Date getStartAt() { 
        return startsAtEpochMs > 0 ? new Date(startsAtEpochMs) : null; 
    }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    @Nullable public String getNotes() { return notes; }
    public void setNotes(@Nullable String notes) { this.notes = notes; }

    @Nullable public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(@Nullable String posterUrl) { this.posterUrl = posterUrl; }

    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    public long getCreatedAtEpochMs() { return createdAtEpochMs; }
    public void setCreatedAtEpochMs(long createdAtEpochMs) { this.createdAtEpochMs = createdAtEpochMs; }

    @Nullable public String getQrPayload() { return qrPayload; }
    public void setQrPayload(@Nullable String qrPayload) { this.qrPayload = qrPayload; }
}
