// AdminEventManagementActivity.java
package com.example.eventease.admin.event.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.event.data.AdminEventDatabaseController;
import com.example.eventease.admin.event.data.Event;

import java.util.ArrayList;
import java.util.List;

public class AdminEventManagementActivity extends AppCompatActivity {

    private AdminEventDatabaseController AEDC = new AdminEventDatabaseController();
    List<Event> events;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_event_management);

        events = AEDC.getEvents();
        RecyclerView rv = findViewById(R.id.rvEvents);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            EventAdapter adapter = new EventAdapter(this, events);
            rv.setAdapter(adapter);
        }
    }
}
