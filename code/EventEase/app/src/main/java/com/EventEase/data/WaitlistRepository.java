package com.EventEase.data;

import com.google.android.gms.tasks.Task;

public interface WaitlistRepository {
    Task<Void> join(String eventId, String uid);
    Task<Boolean> isJoined(String eventId, String uid);
    Task<Void> leave(String eventId, String uid);
}
