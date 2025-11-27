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
    private static final String CHANNEL_ID = NotificationChannelManager.CHANNEL_ID;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "=== FCM Message Received ===");
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        Log.d(TAG, "Message ID: " + remoteMessage.getMessageId());
        Log.d(TAG, "Has notification payload: " + (remoteMessage.getNotification() != null));
        Log.d(TAG, "Has data payload: " + (remoteMessage.getData().size() > 0));
        Log.d(TAG, "Data: " + remoteMessage.getData());
        
        // IMPORTANT: When app is in foreground, onMessageReceived is called
        // When app is in background and notification payload exists, Android handles it automatically
        // We should still process it to ensure it's shown correctly
        
        // CRITICAL: Ensure notification channel is enabled BEFORE processing
        ensureNotificationChannelEnabled();
        
        // Log app state
        android.app.ActivityManager.RunningAppProcessInfo appProcessInfo = new android.app.ActivityManager.RunningAppProcessInfo();
        android.app.ActivityManager.getMyMemoryState(appProcessInfo);
        Log.d(TAG, "App importance: " + appProcessInfo.importance);
        Log.d(TAG, "App in foreground: " + (appProcessInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND));

        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            Log.d(TAG, "Notification payload - Title: " + title);
            Log.d(TAG, "Notification payload - Body: " + body);
            
            showNotification(title, body, remoteMessage.getData());
        } else if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Data-only payload received");
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            
            String type = remoteMessage.getData().get("type");
            String eventId = remoteMessage.getData().get("eventId");
            String eventTitle = remoteMessage.getData().get("eventTitle");
            
            if ("invitation".equals(type)) {
                String title = "You are chosen for the " + (eventTitle != null ? eventTitle : "event") + " event";
                String body = "You've been selected! Tap to view details and accept your invitation.";
                showNotification(title, body, remoteMessage.getData());
            } else if ("waitlist".equals(type) || "selected".equals(type) || "cancelled".equals(type) || 
                      "nonSelected".equals(type) || "replacement".equals(type)) {
                // Handle organizer notifications (including replacement notifications)
                // These come from Cloud Functions with notification payload, but handle data-only as fallback
                String title = remoteMessage.getData().get("title");
                String message = remoteMessage.getData().get("message");
                if (title == null) {
                    title = "Event Update";
                }
                if (message == null) {
                    message = "You have an update regarding " + (eventTitle != null ? eventTitle : "an event") + ".";
                }
                showNotification(title, message, remoteMessage.getData());
            } else {
                // Generic notification fallback
                String title = remoteMessage.getData().get("title");
                String body = remoteMessage.getData().get("body");
                if (title == null) title = "Event Update";
                if (body == null) body = "You have a new notification.";
                showNotification(title, body, remoteMessage.getData());
            }
        }
    }
    
    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);
        FCMTokenManager.getInstance().saveTokenToFirestore(token);
    }

    private void ensureNotificationChannelEnabled() {
        // Use centralized channel manager
        NotificationChannelManager.createNotificationChannel(this);
    }
    
    private void createNotificationChannel() {
        // Use centralized channel manager
        NotificationChannelManager.createNotificationChannel(this);
    }

    private void showNotification(String title, String body, java.util.Map<String, String> data) {
        Log.d(TAG, "=== Attempting to show notification ===");
        Log.d(TAG, "Title: " + title);
        Log.d(TAG, "Body: " + body);
        
        // Check if notifications are enabled for this app
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager is null - cannot show notification");
            return;
        }
        
        // Check if notifications are enabled (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            boolean notificationsEnabled = notificationManager.areNotificationsEnabled();
            Log.d(TAG, "Notifications enabled for app: " + notificationsEnabled);
            if (!notificationsEnabled) {
                Log.w(TAG, "Notifications are disabled for this app - notification will not be shown");
                Log.w(TAG, "  User needs to enable notifications in device settings");
                return;
            }
        }
        
        // Additional check for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.content.pm.PackageManager pm = getPackageManager();
            boolean hasPermission = pm.checkPermission(
                android.Manifest.permission.POST_NOTIFICATIONS,
                getPackageName()
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "POST_NOTIFICATIONS permission granted: " + hasPermission);
            if (!hasPermission) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted - notification will not be shown");
                return;
            }
        }
        
        // Check channel (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationChannelManager.getChannelImportance(this);
            Log.d(TAG, "Channel importance: " + importance + " (0=NONE, 1=MIN, 2=LOW, 3=DEFAULT, 4=HIGH)");
            
            if (importance == NotificationManager.IMPORTANCE_NONE) {
                Log.e(TAG, "Channel importance is NONE - channel is BLOCKED!");
                Log.e(TAG, "  Attempting to fix channel...");
                
                // Try to fix the channel by deleting and recreating
                ensureNotificationChannelEnabled();
                
                // Wait a bit for Android to process the channel recreation
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Re-check after fix attempt
                importance = NotificationChannelManager.getChannelImportance(this);
                if (importance == NotificationManager.IMPORTANCE_NONE) {
                    Log.e(TAG, "Channel still blocked after recreation attempt");
                    Log.e(TAG, "  IMPORTANT: Even though channel is blocked, we'll still try to show notification");
                    Log.e(TAG, "  Android might still display it, or user needs to enable channel in settings");
                    Log.e(TAG, "  Solution: Settings → Apps → EventEase → Notifications → Event Invitations");
                    // DON'T return - continue and try to show notification anyway
                    // Sometimes Android will still show it even if channel appears blocked
                } else {
                    Log.d(TAG, "Channel fixed! Importance is now: " + importance);
                }
            } else if (importance < 0) {
                Log.w(TAG, "Channel not found, creating it now");
                createNotificationChannel();
            } else {
                Log.d(TAG, "Channel is enabled (importance: " + importance + "), notification will be shown");
            }
        }
        
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

        // Check if icon resource exists
        int iconResId = R.drawable.entrant_ic_launcher_foreground;
        try {
            // Verify icon exists
            android.content.res.Resources res = getResources();
            android.graphics.drawable.Drawable icon = res.getDrawable(iconResId, null);
            Log.d(TAG, "  Notification icon exists: " + (icon != null));
        } catch (Exception e) {
            Log.e(TAG, "  Notification icon not found, using default");
            iconResId = android.R.drawable.ic_dialog_info;
        }
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconResId)
                .setContentTitle(title != null ? title : "Event Invitation")
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(body)); // Make it expandable

        int notificationId = (int) System.currentTimeMillis();
        try {
            android.app.Notification notification = notificationBuilder.build();
            notificationManager.notify(notificationId, notification);
            Log.d(TAG, "Notification displayed successfully!");
            Log.d(TAG, "  Notification ID: " + notificationId);
            Log.d(TAG, "  Title: " + title);
            Log.d(TAG, "  Body: " + body);
            Log.d(TAG, "  Channel: " + CHANNEL_ID);
            Log.d(TAG, "  Priority: " + notification.priority);
            Log.d(TAG, "  Flags: " + notification.flags);
            
            // Verify notification was actually posted
            android.service.notification.StatusBarNotification[] activeNotifications = 
                notificationManager.getActiveNotifications();
            boolean found = false;
            for (android.service.notification.StatusBarNotification active : activeNotifications) {
                if (active.getId() == notificationId) {
                    found = true;
                    Log.d(TAG, "  Notification confirmed in active notifications list");
                    break;
                }
            }
            if (!found) {
                Log.w(TAG, "  Notification was posted but not found in active notifications list");
                Log.w(TAG, "  This may indicate the notification was blocked or dismissed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to display notification", e);
            Log.e(TAG, "  Exception type: " + e.getClass().getName());
            Log.e(TAG, "  Exception message: " + e.getMessage());
            if (e.getCause() != null) {
                Log.e(TAG, "  Cause: " + e.getCause().getMessage());
            }
        }
        Log.d(TAG, "=== End showNotification ===");
    }
}

