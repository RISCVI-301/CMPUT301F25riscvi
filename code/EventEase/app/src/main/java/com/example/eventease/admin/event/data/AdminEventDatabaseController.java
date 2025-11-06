package com.example.eventease.admin.event.data;

import java.util.ArrayList;
import java.util.List;

public class AdminEventDatabaseController {

    List<Event> data = new ArrayList<>();


    public List<Event> getEvents(){

        long now = System.currentTimeMillis();
        long oneDay = 24L * 60 * 60 * 1000;

        // Event 1
        data.add(new Event(
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
        data.add(new Event(
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
        data.add(new Event(
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

        return data;
    }

    public boolean deleteEvent(Event obj){
        return true;
    }


}
