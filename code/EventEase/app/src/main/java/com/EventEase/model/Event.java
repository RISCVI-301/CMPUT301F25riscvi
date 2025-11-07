package com.EventEase.model;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an event in the EventEase system.
 * Contains event details including title, location, capacity, waitlist, and admitted entrants.
 */
public class Event {
    /** Unique event identifier. */
    public String id;
    /** Event title. */
    public String title;
    /** Event start time in milliseconds UTC. */
    public long startsAtEpochMs;
    /** Event location. */
    public String location;
    /** Maximum capacity of the event. */
    public int capacity;
    /** Current number of people on waitlist (deprecated - use waitlist.size()). */
    public int waitlistCount;
    /** List of user IDs on the waitlist. */
    public List<String> waitlist;
    /** List of user IDs who have been admitted to the event. */
    public List<String> admitted;
    /** Event notes or description. */
    @Nullable public String notes;
    /** Event-specific rules and requirements. */
    @Nullable public String guidelines;
    /** URL of the event poster image. */
    @Nullable public String posterUrl;
    /** Unique identifier of the event organizer. */
    public String organizerId;
    /** Event creation timestamp in milliseconds UTC. */
    public long createdAtEpochMs;
    /** QR code payload string (e.g., "event:<id>"). */
    @Nullable public String qrPayload;

    public Event() { /* for Firestore */ }

    /**
     * Creates a new draft event for the specified organizer.
     *
     * @param organizerId the unique identifier of the event organizer
     * @return a new Event instance with initialized waitlist and admitted lists
     */
    public static Event newDraft(String organizerId) {
        Event e = new Event();
        e.id = UUID.randomUUID().toString();
        e.organizerId = organizerId;
        e.createdAtEpochMs = System.currentTimeMillis();
        e.waitlist = new ArrayList<>();
        e.admitted = new ArrayList<>();
        return e;
    }

    /**
     * Converts this event to a Map representation for Firestore storage.
     *
     * @return a Map containing all event fields
     */
    public Map<String,Object> toMap() {
        Map<String,Object> m = new HashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("startsAtEpochMs", startsAtEpochMs);
        m.put("location", location);
        m.put("capacity", capacity);
        m.put("waitlistCount", waitlistCount);
        m.put("waitlist", waitlist != null ? waitlist : new ArrayList<>());
        m.put("admitted", admitted != null ? admitted : new ArrayList<>());
        m.put("notes", notes);
        m.put("guidelines", guidelines);
        m.put("posterUrl", posterUrl);
        m.put("organizerId", organizerId);
        m.put("createdAtEpochMs", createdAtEpochMs);
        m.put("qrPayload", qrPayload);
        return m;
    }
    
    /**
     * Creates an Event instance from a Map representation.
     *
     * @param m the Map containing event data
     * @return an Event instance, or null if the map is null
     */
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
        
        // Read waitlist array
        Object waitlistObj = m.get("waitlist");
        if (waitlistObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> waitlistList = (List<String>) waitlistObj;
            e.waitlist = waitlistList != null ? new ArrayList<>(waitlistList) : new ArrayList<>();
        } else {
            e.waitlist = new ArrayList<>();
        }
        
        // Read admitted array
        Object admittedObj = m.get("admitted");
        if (admittedObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> admittedList = (List<String>) admittedObj;
            e.admitted = admittedList != null ? new ArrayList<>(admittedList) : new ArrayList<>();
        } else {
            e.admitted = new ArrayList<>();
        }
        
        // Sync waitlistCount with waitlist size if not set
        if (e.waitlistCount == 0 && e.waitlist != null && !e.waitlist.isEmpty()) {
            e.waitlistCount = e.waitlist.size();
        }
        
        e.notes = (String) m.get("notes");
        e.guidelines = (String) m.get("guidelines");
        e.posterUrl = (String) m.get("posterUrl");
        e.organizerId = (String) m.get("organizerId");
        
        Object createdAt = m.get("createdAtEpochMs");
        e.createdAtEpochMs = createdAt != null ? ((Number) createdAt).longValue() : 0;
        
        e.qrPayload = (String) m.get("qrPayload");
        
        return e;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public long getStartsAtEpochMs() { return startsAtEpochMs; }
    public void setStartsAtEpochMs(long startsAtEpochMs) { this.startsAtEpochMs = startsAtEpochMs; }

    /**
     * Returns the event start time as a Date object.
     *
     * @return the start date, or null if startsAtEpochMs is 0 or negative
     */
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

    /**
     * Returns the list of user IDs on the waitlist.
     *
     * @return a list of user IDs, never null
     */
    public List<String> getWaitlist() { 
        return waitlist != null ? waitlist : new ArrayList<>(); 
    }
    
    /**
     * Sets the waitlist and automatically updates waitlistCount.
     *
     * @param waitlist the list of user IDs on the waitlist
     */
    public void setWaitlist(List<String> waitlist) { 
        this.waitlist = waitlist != null ? new ArrayList<>(waitlist) : new ArrayList<>();
        // Sync waitlistCount
        if (this.waitlist != null) {
            this.waitlistCount = this.waitlist.size();
        }
    }

    /**
     * Returns the list of user IDs who have been admitted to the event.
     *
     * @return a list of user IDs, never null
     */
    public List<String> getAdmitted() { 
        return admitted != null ? admitted : new ArrayList<>(); 
    }
    
    /**
     * Sets the list of admitted user IDs.
     *
     * @param admitted the list of user IDs who have been admitted
     */
    public void setAdmitted(List<String> admitted) { 
        this.admitted = admitted != null ? new ArrayList<>(admitted) : new ArrayList<>();
    }
}
