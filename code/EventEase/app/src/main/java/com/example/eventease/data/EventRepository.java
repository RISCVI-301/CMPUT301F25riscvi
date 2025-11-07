package com.example.eventease.data;

import com.example.eventease.model.Event;
import com.google.android.gms.tasks.Task;

import java.util.Date;
import java.util.List;

/**
 * Repository interface for event data operations.
 * Provides methods to retrieve events and listen to waitlist count updates.
 */
public interface EventRepository {
    Task<List<Event>> getOpenEvents(Date now);
    Task<Event> getEvent(String eventId);
    ListenerRegistration listenWaitlistCount(String eventId, WaitlistCountListener l);
    Task<Void> create(Event event);
}
