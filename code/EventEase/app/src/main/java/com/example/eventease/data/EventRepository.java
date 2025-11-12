package com.example.eventease.data;

import com.example.eventease.model.Event;
import com.google.android.gms.tasks.Task;

import java.util.Date;
import java.util.List;

/**
 * Repository interface for event data operations.
 * Provides methods to retrieve events, create events, and listen to waitlist count updates.
 * This interface follows the Repository pattern to abstract data access operations.
 */
public interface EventRepository {
    /**
     * Retrieves all events that are currently open for registration.
     * An event is considered open if the current time is within its registration period.
     *
     * @param now the current date/time to compare against event registration periods
     * @return a Task that completes with a list of open events
     */
    Task<List<Event>> getOpenEvents(Date now);

    /**
     * Retrieves a single event by its unique identifier.
     *
     * @param eventId the unique event identifier
     * @return a Task that completes with the Event, or null if not found
     */
    Task<Event> getEvent(String eventId);

    /**
     * Registers a listener to receive waitlist count updates for a specific event.
     * The listener will be called whenever the waitlist count changes.
     *
     * @param eventId the unique event identifier
     * @param l the listener to receive waitlist count updates
     * @return a ListenerRegistration that can be used to stop listening
     */
    ListenerRegistration listenWaitlistCount(String eventId, WaitlistCountListener l);

    /**
     * Creates a new event in the data store.
     *
     * @param event the event to create (must have a valid ID and organizerId)
     * @return a Task that completes when the event is created
     */
    Task<Void> create(Event event);
}
