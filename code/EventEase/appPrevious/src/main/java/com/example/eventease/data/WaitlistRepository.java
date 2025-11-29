package com.example.eventease.data;

import com.google.android.gms.tasks.Task;

/**
 * Repository interface for waitlist operations.
 * Provides methods to join, leave, and check waitlist status for events.
 * This interface follows the Repository pattern to abstract data access operations.
 */
public interface WaitlistRepository {
    /**
     * Adds a user to the waitlist for an event.
     *
     * @param eventId the unique event identifier
     * @param uid the unique user identifier
     * @return a Task that completes when the user is added to the waitlist
     */
    Task<Void> join(String eventId, String uid);

    /**
     * Checks if a user is currently on the waitlist for an event.
     *
     * @param eventId the unique event identifier
     * @param uid the unique user identifier
     * @return a Task that completes with true if the user is on the waitlist, false otherwise
     */
    Task<Boolean> isJoined(String eventId, String uid);

    /**
     * Removes a user from the waitlist for an event.
     *
     * @param eventId the unique event identifier
     * @param uid the unique user identifier
     * @return a Task that completes when the user is removed from the waitlist
     */
    Task<Void> leave(String eventId, String uid);
}
