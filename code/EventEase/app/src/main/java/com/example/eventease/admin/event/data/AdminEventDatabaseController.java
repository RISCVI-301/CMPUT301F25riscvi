package com.example.eventease.admin.event.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.example.eventease.model.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                        // Use Event.fromMap() instead of manual construction
                        Map<String, Object> data = d.getData();
                        if (data != null) {
                            data.put("id", d.getId());  // Ensure ID is set
                            Event event = Event.fromMap(data);
                            if (event != null) {
                                list.add(event);
                            }
                        }
                    }
                    cb.onLoaded(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "fetchEvents: read fail", e);
                    cb.onError(e);
                });
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