package com.example.eventease.admin.logs.data;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdminNotificationLogDatabaseController {

    private static final String TAG = "AdminNotifLogDbCtrl";
    private static final String COLLECTION_NOTIFICATION_REQUESTS = "notificationRequests";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();


    public List<Notification> getNotifications() {
        List<Notification> notifications = new ArrayList<>();

        try {
            Task<QuerySnapshot> task = db.collection(COLLECTION_NOTIFICATION_REQUESTS)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get();

            // Wait for Firestore to complete (blocking)
            QuerySnapshot querySnapshot = Tasks.await(task, 10, TimeUnit.SECONDS);

            if (querySnapshot != null) {
                for (DocumentSnapshot document : querySnapshot.getDocuments()) {

                    Long createdAtLong = document.getLong("createdAt");
                    long createdAt = createdAtLong != null ? createdAtLong : 0L;

                    String title = stringOrEmpty(document.getString("title"));
                    String message = stringOrEmpty(document.getString("message"));
                    String eventTitle = stringOrEmpty(document.getString("eventTitle"));
                    String organizerId = stringOrEmpty(document.getString("organizerId"));

                    Notification notification = new Notification(
                            createdAt,
                            title,
                            message,
                            eventTitle,
                            organizerId
                    );

                    String organizerName = document.getString("organizerName");
                    if (organizerName != null && !organizerName.isEmpty()) {
                        notification.setOrganizerName(organizerName);
                    }

                    notifications.add(notification);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load notifications", e);
        }

        return notifications;
    }

    private String stringOrEmpty(String value) {
        return value != null ? value : "";
    }
}