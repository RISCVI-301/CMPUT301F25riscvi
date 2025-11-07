package com.EventEase.data;

import com.EventEase.model.Profile;
import com.google.android.gms.tasks.Task;

/**
 * Repository interface for user profile operations.
 * Provides methods to retrieve and update user profiles.
 */
public interface ProfileRepository {
    Task<Profile> get(String uid);
    Task<Void> upsert(String uid, Profile p);
}
