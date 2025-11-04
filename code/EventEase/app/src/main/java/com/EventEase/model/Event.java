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
    public int waitlistCount; // Current number of people on waitlist
    @Nullable public String notes;
    @Nullable public String guidelines; // Event-specific rules and requirements
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
        m.put("waitlistCount", waitlistCount);
        m.put("notes", notes);
        m.put("guidelines", guidelines);
        m.put("posterUrl", posterUrl);
        m.put("organizerId", organizerId);
        m.put("createdAtEpochMs", createdAtEpochMs);
        m.put("qrPayload", qrPayload);
        return m;
    }
    
    public static Event fromMap(Map<String, Object> m) {
        if (m == null) return null;
        
        Event e = new Event();
        e.id = (String) m.get("id");
        e.title = (String) m.get("title");
        
        Object startsAt = m.get("startsAtEpochMs");
        e.startsAtEpochMs = startsAt != null ? ((Number) startsAt).longValue() : 0;
        
        e.location = (String) m.get("location");
        
        Object cap = m.get("capacity");
        e.capacity = cap != null ? ((Number) cap).intValue() : 0;
        
        Object wc = m.get("waitlistCount");
        e.waitlistCount = wc != null ? ((Number) wc).intValue() : 0;
        
        e.notes = (String) m.get("notes");
        e.guidelines = (String) m.get("guidelines");
        e.posterUrl = (String) m.get("posterUrl");
        e.organizerId = (String) m.get("organizerId");
        
        Object createdAt = m.get("createdAtEpochMs");
        e.createdAtEpochMs = createdAt != null ? ((Number) createdAt).longValue() : 0;
        
        e.qrPayload = (String) m.get("qrPayload");
        
        return e;
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

    public int getWaitlistCount() { return waitlistCount; }
    public void setWaitlistCount(int waitlistCount) { this.waitlistCount = waitlistCount; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    @Nullable public String getNotes() { return notes; }
    public void setNotes(@Nullable String notes) { this.notes = notes; }

    @Nullable public String getGuidelines() { return guidelines; }
    public void setGuidelines(@Nullable String guidelines) { this.guidelines = guidelines; }

    @Nullable public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(@Nullable String posterUrl) { this.posterUrl = posterUrl; }

    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    public long getCreatedAtEpochMs() { return createdAtEpochMs; }
    public void setCreatedAtEpochMs(long createdAtEpochMs) { this.createdAtEpochMs = createdAtEpochMs; }

    @Nullable public String getQrPayload() { return qrPayload; }
    public void setQrPayload(@Nullable String qrPayload) { this.qrPayload = qrPayload; }
}
