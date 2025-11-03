package com.example.eventease.data;

import com.example.eventease.model.Event;
import com.google.android.gms.tasks.Task;

public interface EventRepository {
    Task<Void> create(Event event);
}