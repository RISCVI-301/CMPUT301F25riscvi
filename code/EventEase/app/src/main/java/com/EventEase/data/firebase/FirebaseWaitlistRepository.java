package com.EventEase.data.firebase;

import android.util.Log;

import com.EventEase.data.WaitlistRepository;
import com.EventEase.model.WaitlistEntry;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FirebaseWaitlistRepository
 * Now uses Firestore for persistent waitlist storage.
 */
public class FirebaseWaitlistRepository implements WaitlistRepository {

    private static final String TAG = "WaitlistRepository";
    private final Set<String> membership = ConcurrentHashMap.newKeySet();
    private final FirebaseEventRepository eventRepo;
    private final FirebaseFirestore db;

    public FirebaseWaitlistRepository(FirebaseEventRepository eventRepo) {
        this.eventRepo = eventRepo;
        this.db = FirebaseFirestore.getInstance();
    }

    private static String key(String eventId, String uid) {
        return eventId + "||" + uid;
    }

    @Override
    public Task<Void> join(String eventId, String uid) {
        String docId = eventId + "_" + uid;
        
        Map<String, Object> waitlistData = new HashMap<>();
        waitlistData.put("eventId", eventId);
        waitlistData.put("uid", uid);
        waitlistData.put("joinedAt", System.currentTimeMillis());
        
        return db.collection("waitlists")
                .document(docId)
                .set(waitlistData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User " + uid + " joined waitlist for event " + eventId);
                    membership.add(key(eventId, uid));
                    eventRepo.incrementWaitlist(eventId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to join waitlist", e);
                })
                .continueWith(task -> null);
    }

    @Override
    public Task<Boolean> isJoined(String eventId, String uid) {
        // Check in-memory cache first
        if (membership.contains(key(eventId, uid))) {
            return Tasks.forResult(true);
        }
        
        // Query Firestore
        String docId = eventId + "_" + uid;
        return db.collection("waitlists")
                .document(docId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        boolean exists = task.getResult().exists();
                        if (exists) {
                            membership.add(key(eventId, uid));
                        }
                        return exists;
                    }
                    return false;
                });
    }
}
