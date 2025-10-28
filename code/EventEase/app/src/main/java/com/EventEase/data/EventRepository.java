package com.EventEase.data;

import com.EventEase.model.Event;
import com.google.android.gms.tasks.Task;

import java.util.Date;
import java.util.List;

public interface EventRepository {
    Task<List<Event>> getOpenEvents(Date now);
    Task<Event> getEvent(String eventId);
    ListenerRegistration listenWaitlistCount(String eventId, WaitlistCountListener l);
}
