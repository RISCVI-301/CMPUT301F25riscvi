package com.example.eventease.admin.event.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class AdminEventDatabaseController {

    private static final String TAG = "AdminEventDB";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface EventsCallback {
        void onLoaded(@NonNull List<Event> events);
        void onError(@NonNull Exception e);
    }


    public void fetchEvents(@NonNull final EventsCallback cb) {
        // Device auth - profile check not needed here

        db.collection("events")
                .get()
                .addOnSuccessListener((QuerySnapshot qs) -> {
                    List<Event> list = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String id = d.getId();
                        Number capN = (Number) d.get("capacity");
                        int capacity = capN != null ? capN.intValue() : 0;

                        Number createdN = (Number) d.get("createdAtEpochMs");
                        long createdAt = createdN != null ? createdN.longValue() : 0L;

                        String description = getStr(d, "notes");
                        Boolean geoB = (Boolean) d.get("geolocation");
                        boolean geolocation = geoB != null && geoB;

                        String organizerId = getStr(d, "organizerId");
                        String posterUrl = getStr(d, "posterUrl");

                        Boolean qrEnB = (Boolean) d.get("qrEnabled");
                        boolean qrEnabled = qrEnB != null && qrEnB;

                        String qrPayload = getStr(d, "qrPayload");

                        Number regEndN = (Number) d.get("registrationEnd");
                        long registrationEnd = regEndN != null ? regEndN.longValue() : 0L;

                        Number regStartN = (Number) d.get("registrationStart");
                        long registrationStart = regStartN != null ? regStartN.longValue() : 0L;

                        String title = getStr(d, "title");

                        String guidelines = getStr(d, "guidelines");
                        Number waitlistCountN = (Number) d.get("waitlistCount");
                        int waitlistCount = waitlistCountN != null ? waitlistCountN.intValue() : 0;

                        list.add(new Event(
                                capacity,
                                createdAt,
                                description,
                                geolocation,
                                id,
                                organizerId,
                                posterUrl,
                                qrEnabled,
                                qrPayload,
                                registrationEnd,
                                registrationStart,
                                title,
                                guidelines,
                                waitlistCount
                        ));
                    }
                    cb.onLoaded(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "fetchEvents: read fail", e);
                    cb.onError(e);
                });
    }

    private static String getStr(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v != null ? String.valueOf(v) : "";
    }

    public List<Event> getEvents() {
        return new ArrayList<>();
    }

    public boolean deleteEvent(@NonNull Event obj) {
        String id = obj.getId();
        if (id == null || id.isEmpty()) return false;

        db.collection("events")
                .document(id)
                .delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Deleted event: " + id))
                .addOnFailureListener(e -> Log.e(TAG, "Delete failed for " + id, e));
        return true;
    }

}