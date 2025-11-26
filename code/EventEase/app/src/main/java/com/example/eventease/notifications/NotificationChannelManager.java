package com.example.eventease.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Centralized manager for notification channels.
 * Ensures channels are created consistently across the app.
 */
public class NotificationChannelManager {
    private static final String TAG = "NotificationChannelManager";
    public static final String CHANNEL_ID = "event_invitations";
    public static final String CHANNEL_NAME = "Event Invitations";
    
    /**
     * Creates or ensures the notification channel exists with proper settings.
     * This should be called early in the app lifecycle.
     * 
     * Note: Once a user manually disables a channel in Android settings,
     * the app cannot programmatically re-enable it. The user must do it manually.
     */
    public static void createNotificationChannel(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null, cannot create channel");
            return;
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Channels not needed for Android < 8.0
            return;
        }
        
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager is null, cannot create channel");
            return;
        }
        
        NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
        
        if (channel == null) {
            // Channel doesn't exist, create it with DEFAULT importance (normal notifications)
            // DEFAULT is standard and less likely to be blocked by system/user
            channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for event invitations");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    null
            );
            
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "✓ Notification channel created: " + CHANNEL_ID + " with DEFAULT importance (normal notifications)");
        } else {
            // Channel exists - check its importance
            int currentImportance = channel.getImportance();
            Log.d(TAG, "Notification channel exists: " + CHANNEL_ID);
            Log.d(TAG, "  Current importance: " + currentImportance + " (0=NONE, 1=MIN, 2=LOW, 3=DEFAULT, 4=HIGH)");
            
            // If channel is blocked (NONE), try to fix it
            // Note: On some phones, blocked channels don't show in settings UI
            if (currentImportance == NotificationManager.IMPORTANCE_NONE) {
                Log.w(TAG, "⚠ Channel is BLOCKED (importance = NONE)");
                Log.w(TAG, "  Attempting to recreate channel...");
                
                // Delete the blocked channel
                try {
                    notificationManager.deleteNotificationChannel(CHANNEL_ID);
                    Log.d(TAG, "  Deleted blocked channel");
                    
                    // Small delay to ensure deletion is processed
                    // (Android might cache channel state)
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // Recreate with DEFAULT importance
                    channel = new NotificationChannel(
                            CHANNEL_ID,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_DEFAULT
                    );
                    channel.setDescription("Notifications for event invitations");
                    channel.enableLights(true);
                    channel.enableVibration(true);
                    channel.setShowBadge(true);
                    channel.setSound(
                            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                            null
                    );
                    
                    // Mark channel as important so it shows in settings
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        channel.setShowBadge(true);
                    }
                    
                    notificationManager.createNotificationChannel(channel);
                    Log.d(TAG, "  Channel creation call completed");
                    
                    // Additional delay before verification (Android may need time to process)
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // Verify it was created properly
                    NotificationChannel verifyChannel = notificationManager.getNotificationChannel(CHANNEL_ID);
                    Log.d(TAG, "  Verification: channel exists = " + (verifyChannel != null));
                    
                    if (verifyChannel != null) {
                        int newImportance = verifyChannel.getImportance();
                        Log.d(TAG, "  Verification: new importance = " + newImportance + " (0=NONE, 1=MIN, 2=LOW, 3=DEFAULT, 4=HIGH)");
                        
                        if (newImportance != NotificationManager.IMPORTANCE_NONE) {
                            Log.d(TAG, "✓ Channel recreated successfully with DEFAULT importance");
                            Log.d(TAG, "  New importance: " + newImportance);
                        } else {
                            Log.e(TAG, "✗ Channel recreated but STILL BLOCKED (importance = NONE)");
                            Log.e(TAG, "  This usually means:");
                            Log.e(TAG, "  1. Android system is immediately blocking it");
                            Log.e(TAG, "  2. User has manually disabled it in settings");
                            Log.e(TAG, "  3. Battery optimization is blocking it");
                            Log.e(TAG, "  4. Do Not Disturb mode is blocking it");
                            Log.e(TAG, "  Solution: User must manually enable in:");
                            Log.e(TAG, "  Settings → Apps → EventEase → Notifications");
                            Log.e(TAG, "  If channel doesn't appear, try:");
                            Log.e(TAG, "  1. Uninstall and reinstall the app");
                            Log.e(TAG, "  2. Clear app data: Settings → Apps → EventEase → Storage → Clear Data");
                            Log.e(TAG, "  3. Check Do Not Disturb: Settings → Notifications → Do Not Disturb");
                        }
                    } else {
                        Log.e(TAG, "✗ Channel verification failed - channel not found after recreation");
                        Log.e(TAG, "  This may indicate a system issue or the channel was immediately deleted");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "✗ Failed to recreate channel", e);
                    Log.e(TAG, "  Please manually enable notifications in device settings");
                }
            } else if (currentImportance < NotificationManager.IMPORTANCE_DEFAULT && currentImportance > NotificationManager.IMPORTANCE_NONE) {
                // Channel exists but has low importance (MIN or LOW) - try to update to DEFAULT
                // Note: This may not work if user has manually changed settings
                Log.d(TAG, "  Channel has low importance (" + currentImportance + "), attempting to update to DEFAULT...");
                try {
                    notificationManager.deleteNotificationChannel(CHANNEL_ID);
                    channel = new NotificationChannel(
                            CHANNEL_ID,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_DEFAULT
                    );
                    channel.setDescription("Notifications for event invitations");
                    channel.enableLights(true);
                    channel.enableVibration(true);
                    channel.setShowBadge(true);
                    channel.setSound(
                            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                            null
                    );
                    notificationManager.createNotificationChannel(channel);
                    Log.d(TAG, "✓ Channel updated to DEFAULT importance");
                } catch (Exception e) {
                    Log.w(TAG, "Could not update channel importance (user may have locked it)", e);
                }
            } else {
                // Channel is enabled (DEFAULT, HIGH, or even LOW/MIN is okay - at least it's not NONE)
                Log.d(TAG, "✓ Channel is enabled (importance: " + currentImportance + ")");
            }
        }
    }
    
    /**
     * Checks if the notification channel is enabled and can show notifications.
     * Returns true if channel exists and importance is not NONE.
     */
    public static boolean isChannelEnabled(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true; // Channels not used on older Android
        }
        
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return false;
        }
        
        NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
        if (channel == null) {
            return false;
        }
        
        return channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    
    /**
     * Gets the current importance of the notification channel.
     * Returns -1 if channel doesn't exist or Android < 8.0.
     */
    public static int getChannelImportance(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return NotificationManager.IMPORTANCE_DEFAULT; // Default for older Android
        }
        
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return -1;
        }
        
        NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
        if (channel == null) {
            return -1;
        }
        
        return channel.getImportance();
    }
    
    /**
     * Opens the notification channel settings for this app.
     * On Android 8.0+, this opens directly to the channel settings.
     * On older versions, opens general app notification settings.
     */
    public static void openChannelSettings(Context context) {
        if (context == null) return;
        
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Open directly to the notification channel settings
            intent.setAction(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            // On Android 8.0+, you can also specify the channel ID to open directly to it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID);
            }
        } else {
            // For older Android, open general app settings
            intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            context.startActivity(intent);
            Log.d(TAG, "Opened notification channel settings");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open notification channel settings", e);
            // Fallback to general app settings
            try {
                Intent fallbackIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallbackIntent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallbackIntent);
            } catch (Exception e2) {
                Log.e(TAG, "Failed to open app settings", e2);
            }
        }
    }
}

