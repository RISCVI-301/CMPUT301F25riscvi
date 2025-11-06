package com.example.eventease.admin.event.data;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class AdminEventDatabaseController {

    List<Event> data = new ArrayList<>();


    public List<Event> getEvents() {
        List<Event> data = new ArrayList<>();
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(r ->
                        FirebaseFirestore.getInstance().collection("events").get()
                                .addOnSuccessListener(q -> {
                                    for (DocumentSnapshot d : q.getDocuments()) {
                                        Number capN = (Number) d.get("capacity");
                                        int capacity = capN != null ? capN.intValue() : 0;

                                        Long createdAt = d.getLong("createdAtEpochMs");
                                        Long startsAt = d.getLong("startsAtEpochMs");
                                        long createdAtMs = createdAt != null ? createdAt : 0L;
                                        long regStart = createdAtMs;
                                        long regEnd = startsAt != null ? startsAt : 0L;

                                        data.add(new Event(
                                                capacity,
                                                createdAtMs,
                                                d.getString("notes"),
                                                Boolean.TRUE.equals(d.getBoolean("geolocation")),
                                                d.getString("id") != null ? d.getString("id") : d.getId(),
                                                d.getString("organizerId"),
                                                d.getString("posterUrl"),
                                                Boolean.TRUE.equals(d.getBoolean("qrEnabled")),
                                                d.getString("qrPayload"),
                                                regEnd,
                                                regStart,
                                                d.getString("title")
                                        ));
                                    }
                                    for (Event e : data) {
                                        Log.d("getEvents", "id=" + e.getId() + ", title=" + e.getTitle()
                                                + ", capacity=" + e.getCapacity() + ", createdAt=" + e.getCreatedAt() + ", Photo=" + e.getPosterUrl());
                                    }
                                })
                                .addOnFailureListener(e -> Log.e("getEvents", "read fail", e))
                )
                .addOnFailureListener(e -> Log.e("getEvents", "auth fail", e));
        return data;
    }


    public boolean deleteEvent(Event obj){
        return true;
    }

}
