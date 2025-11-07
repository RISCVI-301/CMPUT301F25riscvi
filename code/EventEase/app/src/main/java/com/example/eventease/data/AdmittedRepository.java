package com.example.eventease.data;

import com.example.eventease.model.Event;
import com.google.android.gms.tasks.Task;

import java.util.List;

/**
 * Repository interface for admitted event operations.
 * Provides methods to admit users to events and retrieve upcoming events.
 */
public interface AdmittedRepository {
    /**
     * Move a user from waitlist to admitted/accepted status
     */
    Task<Void> admit(String eventId, String uid);
    
    /**
     * Check if a user is admitted to an event
     */
    Task<Boolean> isAdmitted(String eventId, String uid);
    
    /**
     * Get all upcoming (admitted) events for a user
     */
    Task<List<Event>> getUpcomingEvents(String uid);
}

