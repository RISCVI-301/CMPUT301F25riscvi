package com.EventEase.data;

import com.google.android.gms.tasks.Task;

/**
 * Repository interface for waitlist operations.
 * Provides methods to join, leave, and check waitlist status.
 */
public interface WaitlistRepository {
    Task<Void> join(String eventId, String uid);
    Task<Boolean> isJoined(String eventId, String uid);
    Task<Void> leave(String eventId, String uid);
}
