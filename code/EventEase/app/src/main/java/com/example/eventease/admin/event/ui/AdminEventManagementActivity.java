// File: AdminEventManagementActivity.java
package com.example.eventease.admin.event.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.event.data.AdminEventDatabaseController;
import com.example.eventease.admin.event.data.Event;

import java.util.List;

public class AdminEventManagementActivity extends AppCompatActivity {

    private AdminEventDatabaseController AEDC = new AdminEventDatabaseController();
    List<Event> events;
    EventAdapter adapter;

    public void deleteEventAndRefresh(@androidx.annotation.NonNull Event event) {
        if (events == null || adapter == null || AEDC == null) return;

        final String id = event.getId();

        // Remove from the Activity's source list by ID
        for (int i = 0; i < events.size(); i++) {
            if (id.equals(events.get(i).getId())) {
                events.remove(i);
                break;
            }
        }

        // Remove from adapter's internal list and update UI
        adapter.removeById(id);

        // Persist/propagate to controller
        AEDC.deleteEvent(event);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_event_management);

        events = AEDC.getEvents();
        RecyclerView rv = findViewById(R.id.rvEvents);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            adapter = new EventAdapter(this, events, this::deleteEventAndRefresh);
            rv.setAdapter(adapter);
        }
    }
}