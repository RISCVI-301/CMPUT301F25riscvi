// AdminEventManagementActivity.java
package com.example.eventease.admin.event.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.event.data.Event;

// If you put EventAdapter in this same package:
import com.example.eventease.admin.event.ui.EventAdapter;

import java.util.ArrayList;
import java.util.List;

public class AdminEventManagementActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_event_management);

        // 1) Create demo events (good titles + prototype stock image URLs)
        long now = System.currentTimeMillis();
        long oneDay = 24L * 60 * 60 * 1000;

        List<Event> demoEvents = new ArrayList<>();

        // Event 1
        demoEvents.add(new Event(
                500,                                // capacity
                now,                                // createdAt
                "A day of talks on the future of AI and product design.", // description
                true,                               // geolocation
                "evt_001",                          // id
                "org_admin",                        // organizerId
                "https://images.unsplash.com/photo-1497493292307-31c376b6e479", // posterUrl
                true,                               // qrEnabled
                "EVT001",                           // qrPayload
                now + 14 * oneDay,                  // registrationEnd
                now - 1 * oneDay,                   // registrationStart
                "Tech Innovators Summit"            // title
        ));

        // Event 2
        demoEvents.add(new Event(
                120,
                now - 2 * oneDay,
                "Hands-on building and networking for indie makers.",
                false,
                "evt_002",
                "org_admin",
                "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee",
                true,
                "EVT002",
                now + 21 * oneDay,
                now - 3 * oneDay,
                "Startup Hack Night"
        ));

        // Event 3
        demoEvents.add(new Event(
                300,
                now - 7 * oneDay,
                "Outdoor music, food trucks, and local artists.",
                true,
                "evt_003",
                "org_admin",
                "https://images.unsplash.com/photo-1472653431158-6364773b2a56",
                false,
                "EVT003",
                now + 30 * oneDay,
                now - 5 * oneDay,
                "Summer Lights Festival"
        ));

        // 2) Hook up RecyclerView with the adapter
        RecyclerView rv = findViewById(R.id.rvEvents);
        if (rv != null) {
            // (Safe even if you already set the layoutManager in XML)
            rv.setLayoutManager(new LinearLayoutManager(this));
            EventAdapter adapter = new EventAdapter(this, demoEvents);
            rv.setAdapter(adapter);
        }
    }
}
