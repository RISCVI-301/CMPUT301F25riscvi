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
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class EventNotificationService extends FirebaseMessagingService {
    private static final String TAG = "EventNotificationService";
    private static final String CHANNEL_ID = "event_invitations";
    private static final String CHANNEL_NAME = "Event Invitations";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            Log.d(TAG, "Message Notification Body: " + body);
            
            showNotification(title, body, remoteMessage.getData());
        } else if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            
            String type = remoteMessage.getData().get("type");
            if ("invitation".equals(type)) {
                String eventId = remoteMessage.getData().get("eventId");
                String eventTitle = remoteMessage.getData().get("eventTitle");
                String title = "Yay! You are chosen for the " + (eventTitle != null ? eventTitle : "event") + " event 🎉";
                String body = "You've been selected! Tap to view details and accept your invitation.";
                showNotification(title, body, remoteMessage.getData());
            }
        }
    }
    
    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);
        FCMTokenManager.getInstance().saveTokenToFirestore(token);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for event invitations");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void showNotification(String title, String body, java.util.Map<String, String> data) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        if (data != null && data.containsKey("eventId")) {
            intent.putExtra("eventId", data.get("eventId"));
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.entrant_ic_launcher_foreground)
                .setContentTitle(title != null ? title : "Event Invitation")
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
    }
}

