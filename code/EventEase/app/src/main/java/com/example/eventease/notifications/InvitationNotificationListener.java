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
    private long listenerStartTime = 0;
    private static final long NOTIFICATION_COOLDOWN = 5000;
    private static final long IGNORE_OLDER_THAN_MS = 60000; // Ignore invitations older than 1 minute when listener starts
    private boolean isFirstSnapshot = true;
    
    public InvitationNotificationListener(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        createNotificationChannel();
    }
    
    public void startListening() {
        com.example.eventease.auth.DeviceAuthManager authManager = 
            new com.example.eventease.auth.DeviceAuthManager(context);
        String uid = authManager.getUid();
        
        if (uid == null || uid.isEmpty()) {
            Log.d(TAG, "No device ID found, cannot listen for invitations");
            return;
        }
        
        if (registration != null) {
            registration.remove();
        }
        
        // Use query without orderBy to avoid index requirement
        // We'll check all PENDING invitations and show notification for the most recent one
        Query query = db.collection("invitations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "PENDING");
        
        // Track when listener starts to ignore old invitations
        listenerStartTime = System.currentTimeMillis();
        isFirstSnapshot = true;
        
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
                
                // Check if this is the first snapshot (initial load with existing documents)
                boolean fromCache = snapshots.getMetadata().isFromCache();
                boolean isInitialLoad = isFirstSnapshot && !fromCache;
                
                if (isInitialLoad) {
                    Log.d(TAG, "Initial snapshot - ignoring existing invitations, only processing NEW ones going forward");
                    isFirstSnapshot = false;
                    // Skip processing existing invitations on first load
                    return;
                }
                
                // After first snapshot, only process truly NEW invitations
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
        
        Log.d(TAG, "Started listening for invitations for user: " + uid + " at time: " + listenerStartTime);
    }
    
    public void stopListening() {
        if (registration != null) {
            registration.remove();
            registration = null;
            isFirstSnapshot = true; // Reset for next time
            listenerStartTime = 0;
            Log.d(TAG, "Stopped listening for invitations");
        }
    }
    
    private void handleNewInvitation(DocumentSnapshot invitationDoc) {
        long currentTime = System.currentTimeMillis();
        
        // Check cooldown
        if (currentTime - lastNotificationTime < NOTIFICATION_COOLDOWN) {
            Log.d(TAG, "Skipping notification due to cooldown");
            return;
        }
        
        // Check if invitation was created before listener started (ignore old invitations)
        Long issuedAt = invitationDoc.getLong("issuedAt");
        if (issuedAt != null && issuedAt > 0 && listenerStartTime > 0) {
            // If invitation was created before listener started, ignore it
            // Add a small buffer (5 seconds) to account for timing differences
            if (issuedAt < (listenerStartTime - 5000)) {
                Log.d(TAG, "Skipping old invitation - created at " + issuedAt + ", listener started at " + listenerStartTime);
                return;
            }
        }
        
        String eventId = invitationDoc.getString("eventId");
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "Processing NEW invitation for event: " + eventId);
        
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
        String title = "You are chosen for the " + (eventTitle != null ? eventTitle : "event") + " event";
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

