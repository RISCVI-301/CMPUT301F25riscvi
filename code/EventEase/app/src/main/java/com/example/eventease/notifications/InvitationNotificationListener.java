package com.example.eventease.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.eventease.MainActivity;
import com.example.eventease.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import javax.annotation.Nullable;

public class InvitationNotificationListener {
    private static final String TAG = "InvitationNotificationListener";
    private static final String CHANNEL_ID = NotificationChannelManager.CHANNEL_ID;
    
    private ListenerRegistration registration;
    private final FirebaseFirestore db;
    private final Context context;
    private long lastNotificationTime = 0;
    private static final long NOTIFICATION_COOLDOWN = 5000;
    
    public InvitationNotificationListener(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        createNotificationChannel();
    }
    
    public void startListening() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "No user logged in, cannot listen for invitations");
            return;
        }
        
        String uid = user.getUid();
        
        if (registration != null) {
            registration.remove();
        }
        
        // Use query without orderBy to avoid index requirement
        // We'll check all PENDING invitations and show notification for the most recent one
        Query query = db.collection("invitations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "PENDING");
        
        registration = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.w(TAG, "Listen failed for invitations", e);
                    // If it's an index error, log it but continue
                    if (e instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException firestoreEx = (FirebaseFirestoreException) e;
                        if (firestoreEx.getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                            Log.w(TAG, "Index required for invitation query, but continuing without orderBy");
                        }
                    }
                    return;
                }
                
                if (snapshots == null) {
                    return;
                }
                
                // Track the most recent invitation to avoid duplicate notifications
                DocumentSnapshot mostRecentInvitation = null;
                long mostRecentTime = 0;
                
                for (com.google.firebase.firestore.DocumentChange change : snapshots.getDocumentChanges()) {
                    if (change.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        DocumentSnapshot doc = change.getDocument();
                        Long issuedAt = doc.getLong("issuedAt");
                        if (issuedAt != null && issuedAt > mostRecentTime) {
                            mostRecentTime = issuedAt;
                            mostRecentInvitation = doc;
                        }
                    }
                }
                
                // Show notification for the most recent new invitation
                if (mostRecentInvitation != null) {
                    handleNewInvitation(mostRecentInvitation);
                }
            }
        });
        
        Log.d(TAG, "Started listening for invitations for user: " + uid);
    }
    
    public void stopListening() {
        if (registration != null) {
            registration.remove();
            registration = null;
            Log.d(TAG, "Stopped listening for invitations");
        }
    }
    
    private void handleNewInvitation(DocumentSnapshot invitationDoc) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNotificationTime < NOTIFICATION_COOLDOWN) {
            Log.d(TAG, "Skipping notification due to cooldown");
            return;
        }
        
        String eventId = invitationDoc.getString("eventId");
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc != null && eventDoc.exists()) {
                        String eventTitle = eventDoc.getString("title");
                        showInvitationNotification(eventId, eventTitle);
                        lastNotificationTime = currentTime;
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load event for notification", e);
                });
    }
    
    private void showInvitationNotification(String eventId, String eventTitle) {
        String title = "Yay! You are chosen for the " + (eventTitle != null ? eventTitle : "event") + " event 🎉";
        String body = "You've been selected! Tap to view details and accept your invitation.";
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("eventId", eventId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.entrant_ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);
        
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
            Log.d(TAG, "Notification shown for event: " + eventTitle);
        }
    }
    
    private void createNotificationChannel() {
        // Use centralized channel manager
        NotificationChannelManager.createNotificationChannel(context);
    }
}

