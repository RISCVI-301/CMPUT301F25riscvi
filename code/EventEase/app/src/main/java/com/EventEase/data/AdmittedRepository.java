package com.EventEase.data;

import com.EventEase.model.Event;
import com.google.android.gms.tasks.Task;

import java.util.List;

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

