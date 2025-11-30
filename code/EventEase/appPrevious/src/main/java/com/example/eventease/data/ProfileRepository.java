package com.example.eventease.data;

import com.example.eventease.model.Profile;
import com.google.android.gms.tasks.Task;

/**
 * Repository interface for user profile operations.
 * Provides methods to retrieve and update user profiles.
 * This interface follows the Repository pattern to abstract data access operations.
 */
public interface ProfileRepository {
    /**
     * Retrieves a user profile by user ID.
     *
     * @param uid the unique user identifier
     * @return a Task that completes with the Profile, or null if not found
     */
    Task<Profile> get(String uid);

    /**
     * Creates or updates a user profile.
     * If a profile with the given UID exists, it will be updated; otherwise, a new profile will be created.
     *
     * @param uid the unique user identifier
     * @param p the profile data to save
     * @return a Task that completes when the profile is saved
     */
    Task<Void> upsert(String uid, Profile p);
}
