// File: AdminEventManagementActivity.java
package com.example.eventease.admin.event.ui;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.event.data.AdminEventDatabaseController;
import com.example.eventease.model.Event;

import java.util.ArrayList;
import java.util.List;

public class AdminEventManagementActivity extends AppCompatActivity {

    private final AdminEventDatabaseController AEDC = new AdminEventDatabaseController();
    private final List<Event> events = new ArrayList<>();
    private RecyclerView rv;
    private EventAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_event_management);

        rv = findViewById(R.id.rvEvents);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            adapter = new EventAdapter(this, new ArrayList<>(), this::deleteEventAndRefresh);
            rv.setAdapter(adapter);
        }

        // Async load; update UI when data is received
        AEDC.fetchEvents(new AdminEventDatabaseController.EventsCallback() {
            @Override
            public void onLoaded(@NonNull List<Event> data) {
                runOnUiThread(() -> {
                    // Replace adapter to avoid relying on internal mutation APIs
                    adapter = new EventAdapter(AdminEventManagementActivity.this, data, AdminEventManagementActivity.this::deleteEventAndRefresh);
                    if (rv != null) rv.setAdapter(adapter);
                });
            }

            @Override
            public void onError(@NonNull Exception e) {
                // TODO: show error state/snackbar if desired
            }
        });
    }

    private void deleteEventAndRefresh(@NonNull Event e) {
        AEDC.deleteEvent(e);
        // Re-fetch to refresh the list after delete
        AEDC.fetchEvents(new AdminEventDatabaseController.EventsCallback() {
            @Override
            public void onLoaded(@NonNull List<Event> data) {
                runOnUiThread(() -> {
                    adapter = new EventAdapter(AdminEventManagementActivity.this, data, AdminEventManagementActivity.this::deleteEventAndRefresh);
                    if (rv != null) rv.setAdapter(adapter);
                });
            }
            @Override public void onError(@NonNull Exception ex) { /* optionally report */ }
        });
    }
}