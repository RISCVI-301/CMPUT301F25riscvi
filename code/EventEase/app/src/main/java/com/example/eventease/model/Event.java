package com.example.eventease.model;

import androidx.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an event in the EventEase system.
 *
 * <p>This class serves as a data model (Data Transfer Object pattern) for events in the EventEase
 * application. It encapsulates all event-related information including scheduling, capacity management,
 * waitlist handling, and attendee tracking. Instances of this class are used throughout the application
 * for event creation, display, and management by both organizers and entrants.</p>
 *
 * <p>This class supports serialization to/from Firestore Map format via {@link #toMap()} and
 * {@link #fromMap(Map)} methods, enabling persistence in the Firebase backend. The class follows
 * the Value Object pattern for immutable data representation, though it uses public fields for
 * Firestore compatibility.</p>
 *
 * <p><b>Role in Application:</b> Central data model for the event management system. Used by
 * organizers to create and manage events, by entrants to view and register for events, and by
 * the backend services for event processing and notifications.</p>
 *
 * <p><b>Outstanding Issues:</b> The {@code waitlistCount} field is deprecated in favor of using
 * {@code waitlist.size()}, but is maintained for backwards compatibility with existing Firestore data.
 * The {@code description} field is a legacy field that mirrors {@code notes}.</p>
 */
public class Event implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Unique event identifier. */
    public String id;
    /** Event title. */
    public String title;
    /** Event start time in milliseconds UTC. */
    public long startsAtEpochMs;
    /** Event deadline in milliseconds UTC. */
    public long deadlineEpochMs;
    /** Registration start time in milliseconds UTC. */
    public long registrationStart;
    /** Registration end time in milliseconds UTC. */
    public long registrationEnd;
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
    /** Event description (legacy field, same as notes). */
    @Nullable public String description;
    /** Event-specific rules and requirements. */
    @Nullable public String guidelines;
    /** URL of the event poster image. */
    @Nullable public String posterUrl;
    /** Unique identifier of the event organizer. */
    public String organizerId;
    /** Event creation timestamp in milliseconds UTC. */
    public long createdAtEpochMs;
    /** QR code payload string (e.g., {@code "event:<id>"}). */
    @Nullable public String qrPayload;
    /** Interests/tags for filtering (e.g., Outdoor, Music). */
    @Nullable public List<String> interests;

    /**
     * Default constructor for Firestore deserialization.
     */
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
        m.put("deadlineEpochMs", deadlineEpochMs);
        m.put("registrationStart", registrationStart);
        m.put("registrationEnd", registrationEnd);
        m.put("location", location);
        m.put("capacity", capacity);
        m.put("waitlistCount", waitlistCount);
        m.put("waitlist", waitlist != null ? waitlist : new ArrayList<>());
        m.put("admitted", admitted != null ? admitted : new ArrayList<>());
        m.put("notes", notes);
        m.put("description", description);
        m.put("guidelines", guidelines);
        m.put("posterUrl", posterUrl);
        m.put("organizerId", organizerId);
        m.put("createdAtEpochMs", createdAtEpochMs);
        m.put("qrPayload", qrPayload);
        m.put("interests", interests != null ? interests : new ArrayList<>());
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
        if (startsAt == null) {
            // Fallback to eventStartEpochMs (with "event" prefix) for backwards compatibility
            startsAt = m.get("eventStartEpochMs");
        }
        if (startsAt == null) {
            // Also check for eventStart (without EpochMs suffix) as another fallback
            startsAt = m.get("eventStart");
        }
        e.startsAtEpochMs = startsAt != null ? ((Number) startsAt).longValue() : 0;
        
        Object deadline = m.get("deadlineEpochMs");
        e.deadlineEpochMs = deadline != null ? ((Number) deadline).longValue() : 0;
        
        Object regStart = m.get("registrationStart");
        e.registrationStart = regStart != null ? ((Number) regStart).longValue() : 0;
        
        Object regEnd = m.get("registrationEnd");
        e.registrationEnd = regEnd != null ? ((Number) regEnd).longValue() : 0;
        
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
        e.description = (String) m.get("description");
        if (e.notes == null && e.description != null) {
            e.notes = e.description;
        }
        if (e.description == null && e.notes != null) {
            e.description = e.notes;
        }
        e.guidelines = (String) m.get("guidelines");
        e.posterUrl = (String) m.get("posterUrl");
        e.organizerId = (String) m.get("organizerId");
        
        Object createdAt = m.get("createdAtEpochMs");
        e.createdAtEpochMs = createdAt != null ? ((Number) createdAt).longValue() : 0;
        
        e.qrPayload = (String) m.get("qrPayload");
        
        Object interestsObj = m.get("interests");
        if (interestsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> interestList = (List<String>) interestsObj;
            e.interests = interestList != null ? new ArrayList<>(interestList) : new ArrayList<>();
        } else {
            e.interests = new ArrayList<>();
        }
        
        return e;
    }

    /**
     * Gets the unique event identifier.
     *
     * @return the event ID, or null if not set
     */
    public String getId() { return id; }

    /**
     * Sets the unique event identifier.
     *
     * @param id the event ID to set
     */
    public void setId(String id) { this.id = id; }

    /**
     * Gets the event title.
     *
     * @return the event title, or null if not set
     */
    public String getTitle() { return title; }

    /**
     * Sets the event title.
     *
     * @param title the event title to set
     */
    public void setTitle(String title) { this.title = title; }

    /**
     * Gets the event start time in milliseconds since epoch (UTC).
     *
     * @return the start time in milliseconds, or 0 if not set
     */
    public long getStartsAtEpochMs() { return startsAtEpochMs; }

    /**
     * Sets the event start time in milliseconds since epoch (UTC).
     *
     * @param startsAtEpochMs the start time in milliseconds
     */
    public void setStartsAtEpochMs(long startsAtEpochMs) { this.startsAtEpochMs = startsAtEpochMs; }

    /**
     * Gets the list of interests/tags associated with the event.
     *
     * @return the list of interests (e.g., Outdoor, Music), or null if not set
     */
    public List<String> getInterests() { return interests; }

    /**
     * Sets the list of interests/tags associated with the event.
     *
     * @param interests the list of interests to set
     */
    public void setInterests(List<String> interests) { this.interests = interests; }

    /**
     * Gets the event deadline in milliseconds since epoch (UTC).
     *
     * @return the deadline in milliseconds, or 0 if not set
     */
    public long getDeadlineEpochMs() { return deadlineEpochMs; }

    /**
     * Sets the event deadline in milliseconds since epoch (UTC).
     *
     * @param deadlineEpochMs the deadline in milliseconds
     */
    public void setDeadlineEpochMs(long deadlineEpochMs) { this.deadlineEpochMs = deadlineEpochMs; }

    /**
     * Gets the registration start time in milliseconds since epoch (UTC).
     *
     * @return the registration start time in milliseconds, or 0 if not set
     */
    public long getRegistrationStart() { return registrationStart; }

    /**
     * Sets the registration start time in milliseconds since epoch (UTC).
     *
     * @param registrationStart the registration start time in milliseconds
     */
    public void setRegistrationStart(long registrationStart) { this.registrationStart = registrationStart; }

    /**
     * Gets the registration end time in milliseconds since epoch (UTC).
     *
     * @return the registration end time in milliseconds, or 0 if not set
     */
    public long getRegistrationEnd() { return registrationEnd; }

    /**
     * Sets the registration end time in milliseconds since epoch (UTC).
     *
     * @param registrationEnd the registration end time in milliseconds
     */
    public void setRegistrationEnd(long registrationEnd) { this.registrationEnd = registrationEnd; }

    /**
     * Returns the event start time as a Date object.
     *
     * @return the start date, or null if startsAtEpochMs is 0 or negative
     */
    public Date getStartAt() { 
        return startsAtEpochMs > 0 ? new Date(startsAtEpochMs) : null; 
    }

    /**
     * Gets the maximum capacity of the event.
     *
     * @return the event capacity
     */
    public int getCapacity() { return capacity; }

    /**
     * Sets the maximum capacity of the event.
     *
     * @param capacity the event capacity to set
     */
    public void setCapacity(int capacity) { this.capacity = capacity; }

    /**
     * Gets the waitlist count (deprecated - use getWaitlist().size() instead).
     *
     * @return the number of users on the waitlist
     */
    public int getWaitlistCount() { return waitlistCount; }

    /**
     * Sets the waitlist count (deprecated - this is automatically synced with waitlist size).
     *
     * @param waitlistCount the waitlist count to set
     */
    public void setWaitlistCount(int waitlistCount) { this.waitlistCount = waitlistCount; }

    /**
     * Gets the event location.
     *
     * @return the event location, or null if not set
     */
    public String getLocation() { return location; }

    /**
     * Sets the event location.
     *
     * @param location the event location to set
     */
    public void setLocation(String location) { this.location = location; }

    /**
     * Gets the event notes or description.
     *
     * @return the event notes, or null if not set
     */
    @Nullable public String getNotes() { return notes; }

    /**
     * Sets the event notes or description.
     *
     * @param notes the event notes to set
     */
    public void setNotes(@Nullable String notes) { this.notes = notes; }

    /**
     * Gets the event description (returns notes if description is null).
     *
     * @return the event description, or notes if description is null, or null if neither is set
     */
    @Nullable public String getDescription() { return description != null ? description : notes; }

    /**
     * Sets the event description.
     *
     * @param description the event description to set
     */
    public void setDescription(@Nullable String description) { this.description = description; }

    /**
     * Gets the event-specific guidelines and requirements.
     *
     * @return the event guidelines, or null if not set
     */
    @Nullable public String getGuidelines() { return guidelines; }

    /**
     * Sets the event-specific guidelines and requirements.
     *
     * @param guidelines the event guidelines to set
     */
    public void setGuidelines(@Nullable String guidelines) { this.guidelines = guidelines; }

    /**
     * Gets the URL of the event poster image.
     *
     * @return the poster URL, or null if not set
     */
    @Nullable public String getPosterUrl() { return posterUrl; }

    /**
     * Sets the URL of the event poster image.
     *
     * @param posterUrl the poster URL to set
     */
    public void setPosterUrl(@Nullable String posterUrl) { this.posterUrl = posterUrl; }

    /**
     * Gets the unique identifier of the event organizer.
     *
     * @return the organizer ID, or null if not set
     */
    public String getOrganizerId() { return organizerId; }

    /**
     * Sets the unique identifier of the event organizer.
     *
     * @param organizerId the organizer ID to set
     */
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    /**
     * Gets the event creation timestamp in milliseconds since epoch (UTC).
     *
     * @return the creation timestamp in milliseconds, or 0 if not set
     */
    public long getCreatedAtEpochMs() { return createdAtEpochMs; }

    /**
     * Sets the event creation timestamp in milliseconds since epoch (UTC).
     *
     * @param createdAtEpochMs the creation timestamp in milliseconds
     */
    public void setCreatedAtEpochMs(long createdAtEpochMs) { this.createdAtEpochMs = createdAtEpochMs; }

    /**
     * Gets the QR code payload string for the event.
     *
     * @return the QR payload (e.g., "event:&lt;id&gt;"), or null if not set
     */
    @Nullable public String getQrPayload() { return qrPayload; }

    /**
     * Sets the QR code payload string for the event.
     *
     * @param qrPayload the QR payload to set
     */
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
