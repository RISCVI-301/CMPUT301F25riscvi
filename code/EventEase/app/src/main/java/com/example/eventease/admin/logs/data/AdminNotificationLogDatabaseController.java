package com.example.eventease.admin.logs.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class AdminNotificationLogDatabaseController {

    private static final String TAG = "AdminNotificationLogDB";
    private static final String COLLECTION_NOTIFICATION_REQUESTS = "notificationRequests";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<Notification> notifications = new ArrayList<>();

    public interface NotificationsCallback {
        void onLoaded(@NonNull List<Notification> notifications);
        void onError(@NonNull Exception e);
    }

    /**
     * Asynchronously fetch all notification logs from Firestore.
     * Call this from your Activity/Fragment and update the adapter
     * in the onLoaded callback.
     */
    public void fetchNotifications(@NonNull final NotificationsCallback cb) {
        db.collection(COLLECTION_NOTIFICATION_REQUESTS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener((QuerySnapshot qs) -> {
                    notifications.clear();

                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Notification n = mapDocumentToNotification(d);
                        if (n != null) {
                            notifications.add(n);
                        }
                    }

                    cb.onLoaded(new ArrayList<>(notifications));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "fetchNotifications: read fail", e);
                    cb.onError(e);
                });
    }

    private Notification mapDocumentToNotification(DocumentSnapshot d) {
        if (d == null) return null;

        Number createdN = (Number) d.get("createdAt");
        long createdAt = createdN != null ? createdN.longValue() : 0L;

        String title = getStr(d, "title");          // Firestore field "title"
        String message = getStr(d, "message");      // Firestore field "message"
        String eventTitle = getStr(d, "eventTitle");
        String organizerId = getStr(d, "organizerId");

        Notification notification =
                new Notification(createdAt, title, message, eventTitle, organizerId);

        // Optional organizer name, if stored
        String organizerName = getStr(d, "organizerName");
        if (!organizerName.isEmpty()) {
            notification.setOrganizerName(organizerName);
        }

        return notification;
    }

    private static String getStr(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v != null ? String.valueOf(v) : "";
    }

    /**
     * Returns the last loaded list of notifications.
     * Call after fetchNotifications() has completed.
     */
    public List<Notification> getNotifications() {
        return new ArrayList<>(notifications);
    }
}