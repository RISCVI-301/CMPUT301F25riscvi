package com.example.eventease.data;

import com.example.eventease.model.Event;
import com.google.android.gms.tasks.Task;

import java.util.List;

/**
 * Repository interface for admitted event operations.
 * Provides methods to admit users to events and retrieve upcoming/previous events.
 * This interface follows the Repository pattern to abstract data access operations.
 */
public interface AdmittedRepository {
    /**
     * Moves a user from waitlist to admitted status for an event.
     * This operation removes the user from the waitlist and adds them to the admitted entrants list.
     *
     * @param eventId the unique event identifier
     * @param uid the unique user identifier
     * @return a Task that completes when the user is admitted
     */
    Task<Void> admit(String eventId, String uid);
    
    /**
     * Checks if a user is admitted to an event.
     *
     * @param eventId the unique event identifier
     * @param uid the unique user identifier
     * @return a Task that completes with true if the user is admitted, false otherwise
     */
    Task<Boolean> isAdmitted(String eventId, String uid);
    
    /**
     * Gets all upcoming events for a user where they are in the AdmittedEntrants collection.
     * An event is considered upcoming if its deadline (or start time) has not yet passed.
     *
     * @param uid the unique user identifier
     * @return a Task that completes with a list of upcoming events the user is admitted to
     */
    Task<List<Event>> getUpcomingEvents(String uid);
    
    /**
     * Gets all previous events for a user where they are in the AdmittedEntrants collection
     * and the event deadline has passed.
     *
     * @param uid the unique user identifier
     * @return a Task that completes with a list of previous events the user was admitted to
     */
    Task<List<Event>> getPreviousEvents(String uid);
}

