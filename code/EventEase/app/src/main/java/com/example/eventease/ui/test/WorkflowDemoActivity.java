package com.example.eventease.ui.test;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Visual demo activity that demonstrates the complete event workflow step-by-step.
 * 
 * This activity simulates the workflow and shows what happens at each stage:
 * 1. Event creation
 * 2. Users joining waitlist
 * 3. Registration period ending
 * 4. Automatic selection
 * 5. Invitation handling
 * 6. Deadline processing
 * 7. Sorry notifications
 */
public class WorkflowDemoActivity extends AppCompatActivity {
    
    private static final String TAG = "WorkflowDemo";
    
    private TextView logTextView;
    private ScrollView scrollView;
    private Button startDemoButton;
    private Button resetButton;
    
    private Handler mainHandler;
    private SimpleDateFormat timeFormat;
    
    // Demo data
    private Map<String, Object> demoEvent;
    private List<String> waitlistedUsers;
    private List<String> selectedUsers;
    private List<String> nonSelectedUsers;
    private Map<String, String> invitationStatus;
    private long baseTime;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workflow_demo);
        
        logTextView = findViewById(R.id.logTextView);
        scrollView = findViewById(R.id.scrollView);
        startDemoButton = findViewById(R.id.startDemoButton);
        resetButton = findViewById(R.id.resetButton);
        
        mainHandler = new Handler(Looper.getMainLooper());
        timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        
        startDemoButton.setOnClickListener(v -> startDemo());
        resetButton.setOnClickListener(v -> resetDemo());
        
        initializeDemo();
    }
    
    private void initializeDemo() {
        baseTime = System.currentTimeMillis();
        waitlistedUsers = new ArrayList<>();
        selectedUsers = new ArrayList<>();
        nonSelectedUsers = new ArrayList<>();
        invitationStatus = new HashMap<>();
        
        // Create demo event
        demoEvent = new HashMap<>();
        demoEvent.put("title", "Demo Event - Workflow Test");
        demoEvent.put("registrationStart", baseTime);
        demoEvent.put("registrationEnd", baseTime + 30000); // 30 seconds
        demoEvent.put("deadlineEpochMs", baseTime + 60000); // 60 seconds
        demoEvent.put("startsAtEpochMs", baseTime + 120000); // 120 seconds
        demoEvent.put("capacity", 5);
        demoEvent.put("sampleSize", 2);
        demoEvent.put("selectionProcessed", false);
        demoEvent.put("selectionNotificationSent", false);
        demoEvent.put("deadlineNotificationSent", false);
        demoEvent.put("sorryNotificationSent", false);
        
        addLog("═══════════════════════════════════════════════════════════");
        addLog("🎬 Workflow Demo Initialized");
        addLog("═══════════════════════════════════════════════════════════");
        addLog("");
        addLog("Event: " + demoEvent.get("title"));
        addLog("Capacity: " + demoEvent.get("capacity"));
        addLog("Sample Size: " + demoEvent.get("sampleSize"));
        addLog("");
        addLog("Timeline:");
        addLog("  Registration Start: " + formatTime((Long) demoEvent.get("registrationStart")));
        addLog("  Registration End: " + formatTime((Long) demoEvent.get("registrationEnd")) + " (30s from now)");
        addLog("  Deadline: " + formatTime((Long) demoEvent.get("deadlineEpochMs")) + " (60s from now)");
        addLog("  Event Start: " + formatTime((Long) demoEvent.get("startsAtEpochMs")) + " (120s from now)");
        addLog("");
        addLog("Click 'Start Demo' to begin the workflow simulation!");
        addLog("");
    }
    
    private void startDemo() {
        startDemoButton.setEnabled(false);
        resetButton.setEnabled(false);
        
        addLog("");
        addLog("═══════════════════════════════════════════════════════════");
        addLog("🚀 Starting Workflow Demo");
        addLog("═══════════════════════════════════════════════════════════");
        addLog("");
        
        // Step 1: Users join waitlist (immediate)
        addLog("📝 STEP 1: Users Joining Waitlist");
        addLog("─────────────────────────────────────────────────────────");
        simulateUsersJoiningWaitlist();
        
        // Step 2: Wait for registration end (after 5 seconds)
        mainHandler.postDelayed(() -> {
            addLog("");
            addLog("⏰ STEP 2: Registration Period Ends");
            addLog("─────────────────────────────────────────────────────────");
            simulateRegistrationEnd();
        }, 5000);
        
        // Step 3: Selection happens (after 6 seconds)
        mainHandler.postDelayed(() -> {
            addLog("");
            addLog("🎲 STEP 3: Automatic Selection");
            addLog("─────────────────────────────────────────────────────────");
            simulateSelection();
        }, 6000);
        
        // Step 4: Invitations sent (after 7 seconds)
        mainHandler.postDelayed(() -> {
            addLog("");
            addLog("📧 STEP 4: Invitations Sent");
            addLog("─────────────────────────────────────────────────────────");
            simulateInvitations();
        }, 7000);
        
        // Step 5: Users respond (after 10 seconds)
        mainHandler.postDelayed(() -> {
            addLog("");
            addLog("👤 STEP 5: Users Respond to Invitations");
            addLog("─────────────────────────────────────────────────────────");
            simulateUserResponses();
        }, 10000);
        
        // Step 6: Deadline passes (after 15 seconds)
        mainHandler.postDelayed(() -> {
            addLog("");
            addLog("⏳ STEP 6: Deadline Passes");
            addLog("─────────────────────────────────────────────────────────");
            simulateDeadlineProcessing();
        }, 15000);
        
        // Step 7: Sorry notifications (after 20 seconds)
        mainHandler.postDelayed(() -> {
            addLog("");
            addLog("😔 STEP 7: Sorry Notifications");
            addLog("─────────────────────────────────────────────────────────");
            simulateSorryNotifications();
        }, 20000);
        
        // Step 8: Final summary (after 25 seconds)
        mainHandler.postDelayed(() -> {
            addLog("");
            addLog("═══════════════════════════════════════════════════════════");
            addLog("✅ Workflow Demo Complete!");
            addLog("═══════════════════════════════════════════════════════════");
            addLog("");
            showFinalSummary();
            resetButton.setEnabled(true);
        }, 25000);
    }
    
    private void simulateUsersJoiningWaitlist() {
        int numUsers = 5;
        for (int i = 1; i <= numUsers; i++) {
            String userId = "user" + i;
            waitlistedUsers.add(userId);
            addLog("  ✓ " + userId + " joined waitlist");
            addLog("     Waitlist count: " + waitlistedUsers.size() + "/" + demoEvent.get("capacity"));
            
            // Check capacity
            if (waitlistedUsers.size() >= (Integer) demoEvent.get("capacity")) {
                addLog("  ⚠️  Capacity reached! No more users can join.");
            }
            
            try {
                Thread.sleep(500); // Small delay for visual effect
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        addLog("");
        addLog("  📊 Total waitlisted: " + waitlistedUsers.size() + " users");
    }
    
    private void simulateRegistrationEnd() {
        long currentTime = System.currentTimeMillis();
        long registrationEnd = (Long) demoEvent.get("registrationEnd");
        
        addLog("  Current time: " + formatTime(currentTime));
        addLog("  Registration end: " + formatTime(registrationEnd));
        
        if (currentTime >= registrationEnd) {
            addLog("  ✅ Registration period has ended!");
            addLog("  🔄 Automatic selection will now trigger...");
        } else {
            addLog("  ⏳ Waiting for registration to end...");
        }
    }
    
    private void simulateSelection() {
        int sampleSize = (Integer) demoEvent.get("sampleSize");
        
        addLog("  🎲 Randomly selecting " + sampleSize + " entrants from " + waitlistedUsers.size() + " waitlisted...");
        
        // Random selection
        List<String> shuffled = new ArrayList<>(waitlistedUsers);
        Collections.shuffle(shuffled, new Random());
        
        selectedUsers = new ArrayList<>(shuffled.subList(0, Math.min(sampleSize, shuffled.size())));
        nonSelectedUsers = new ArrayList<>(waitlistedUsers);
        nonSelectedUsers.removeAll(selectedUsers);
        
        addLog("");
        addLog("  ✅ Selection Complete!");
        addLog("  📋 Selected entrants (" + selectedUsers.size() + "):");
        for (String user : selectedUsers) {
            addLog("     • " + user);
        }
        addLog("");
        addLog("  📋 Non-selected entrants (" + nonSelectedUsers.size() + "):");
        for (String user : nonSelectedUsers) {
            addLog("     • " + user);
        }
        
        demoEvent.put("selectionProcessed", true);
        demoEvent.put("selectionNotificationSent", true);
        
        addLog("");
        addLog("  🔔 Selection notification sent to selected entrants!");
        addLog("  📧 Invitations created for selected entrants");
    }
    
    private void simulateInvitations() {
        addLog("  📧 Sending invitations to selected entrants...");
        
        for (String user : selectedUsers) {
            invitationStatus.put(user, "PENDING");
            addLog("     • " + user + " - Invitation sent (Status: PENDING)");
        }
        
        addLog("");
        addLog("  ✅ All invitations sent!");
        addLog("  ⏰ Deadline to accept/decline: " + formatTime((Long) demoEvent.get("deadlineEpochMs")));
    }
    
    private void simulateUserResponses() {
        addLog("  👤 Users responding to invitations...");
        addLog("");
        
        // User 1 accepts
        if (selectedUsers.size() > 0) {
            String user1 = selectedUsers.get(0);
            invitationStatus.put(user1, "ACCEPTED");
            addLog("  ✅ " + user1 + " - ACCEPTED invitation");
            addLog("     → Moved to AdmittedEntrants");
            addLog("     → Event appears in 'Upcoming Events'");
        }
        
        // User 2 might accept or be pending
        if (selectedUsers.size() > 1) {
            String user2 = selectedUsers.get(1);
            // Keep as PENDING (will be non-responder)
            addLog("  ⏳ " + user2 + " - No response yet (Status: PENDING)");
        }
        
        addLog("");
        addLog("  📊 Invitation Status:");
        for (Map.Entry<String, String> entry : invitationStatus.entrySet()) {
            addLog("     • " + entry.getKey() + ": " + entry.getValue());
        }
    }
    
    private void simulateDeadlineProcessing() {
        long currentTime = System.currentTimeMillis();
        long deadline = (Long) demoEvent.get("deadlineEpochMs");
        
        addLog("  Current time: " + formatTime(currentTime));
        addLog("  Deadline: " + formatTime(deadline));
        addLog("");
        
        if (currentTime >= deadline) {
            addLog("  ✅ Deadline has passed!");
            addLog("  🔄 Processing non-responders...");
            addLog("");
            
            List<String> nonResponders = new ArrayList<>();
            for (Map.Entry<String, String> entry : invitationStatus.entrySet()) {
                if ("PENDING".equals(entry.getValue())) {
                    nonResponders.add(entry.getKey());
                    entry.setValue("DECLINED");
                    addLog("  ❌ " + entry.getKey() + " - No response (moved to CancelledEntrants)");
                }
            }
            
            if (!nonResponders.isEmpty()) {
                addLog("");
                addLog("  🔔 Deadline missed notification sent to " + nonResponders.size() + " non-responders");
            }
            
            demoEvent.put("deadlineNotificationSent", true);
        } else {
            addLog("  ⏳ Deadline not yet reached");
        }
    }
    
    private void simulateSorryNotifications() {
        long currentTime = System.currentTimeMillis();
        long eventStart = (Long) demoEvent.get("startsAtEpochMs");
        long fortyEightHours = 48L * 60 * 60 * 1000;
        long sorryTime = eventStart - fortyEightHours;
        
        addLog("  Current time: " + formatTime(currentTime));
        addLog("  Sorry notification time: " + formatTime(sorryTime) + " (48 hours before event)");
        addLog("");
        
        // For demo, send sorry notification now
        if (nonSelectedUsers.size() > 0) {
            addLog("  😔 Sending 'sorry' notifications to non-selected entrants...");
            addLog("");
            for (String user : nonSelectedUsers) {
                addLog("     • " + user + " - Sorry notification sent");
            }
            addLog("");
            addLog("  ✅ " + nonSelectedUsers.size() + " sorry notifications sent");
            addLog("  📝 Message: 'Oops, the event selection has been done. Better luck next time!'");
        } else {
            addLog("  ℹ️  No non-selected entrants to notify");
        }
        
        demoEvent.put("sorryNotificationSent", true);
    }
    
    private void showFinalSummary() {
        addLog("📊 FINAL SUMMARY");
        addLog("─────────────────────────────────────────────────────────");
        addLog("");
        addLog("Event: " + demoEvent.get("title"));
        addLog("");
        addLog("Waitlisted Users: " + waitlistedUsers.size());
        addLog("  " + waitlistedUsers);
        addLog("");
        addLog("Selected Users: " + selectedUsers.size());
        addLog("  " + selectedUsers);
        addLog("");
        addLog("Non-Selected Users: " + nonSelectedUsers.size());
        addLog("  " + nonSelectedUsers);
        addLog("");
        addLog("Invitation Status:");
        for (Map.Entry<String, String> entry : invitationStatus.entrySet()) {
            addLog("  • " + entry.getKey() + ": " + entry.getValue());
        }
        addLog("");
        addLog("Workflow Flags:");
        addLog("  • Selection Processed: " + demoEvent.get("selectionProcessed"));
        addLog("  • Selection Notification Sent: " + demoEvent.get("selectionNotificationSent"));
        addLog("  • Deadline Notification Sent: " + demoEvent.get("deadlineNotificationSent"));
        addLog("  • Sorry Notification Sent: " + demoEvent.get("sorryNotificationSent"));
        addLog("");
        addLog("✅ All workflow steps completed successfully!");
    }
    
    private void resetDemo() {
        logTextView.setText("");
        startDemoButton.setEnabled(true);
        resetButton.setEnabled(false);
        initializeDemo();
    }
    
    private void addLog(String message) {
        mainHandler.post(() -> {
            String timestamp = timeFormat.format(new Date());
            String logMessage = "[" + timestamp + "] " + message + "\n";
            logTextView.append(logMessage);
            
            // Auto-scroll to bottom
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            
            Log.d(TAG, message);
        });
    }
    
    private String formatTime(long timestamp) {
        return timeFormat.format(new Date(timestamp));
    }
}

