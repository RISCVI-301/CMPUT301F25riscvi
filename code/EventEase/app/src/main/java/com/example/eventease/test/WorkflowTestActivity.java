package com.example.eventease.test;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Visual test activity for the complete workflow.
 * Shows real-time progress as the test runs.
 */
public class WorkflowTestActivity extends AppCompatActivity {
    private static final String TAG = "WorkflowTest";
    
    private TextView tvLog;
    private ScrollView scrollView;
    private StringBuilder logBuilder;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    
    private String testEventId;
    private List<String> testUserIds;
    private String organizerId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workflow_test);
        
        tvLog = findViewById(R.id.tvLog);
        scrollView = findViewById(R.id.scrollView);
        Button btnStart = findViewById(R.id.btnStartTest);
        Button btnCheckState = findViewById(R.id.btnCheckState);
        Button btnCleanup = findViewById(R.id.btnCleanup);
        
        logBuilder = new StringBuilder();
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        
        organizerId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        
        btnStart.setOnClickListener(v -> runWorkflowTest());
        btnCheckState.setOnClickListener(v -> checkCurrentState());
        btnCleanup.setOnClickListener(v -> cleanupTestData());
        
        log("🎬 Workflow Test Ready! (⚡ FAST MODE)");
        log("📱 Organizer ID: " + organizerId);
        log("");
        log("📝 Using existing users:");
        log("   1. shinchan@gmail.com");
        log("   2. himawari@gmail.com");
        log("   3. sanika1234@gmail.com");
        log("   4. chotabheem@gmail.com");
        log("");
        log("⚡ FAST MODE: Registration ends in 15 seconds!");
        log("");
        log("Instructions:");
        log("1. Click 'Start Test' to create event and add users");
        log("2. Wait ~15 seconds for registration to end");
        log("3. Selection happens automatically within 1 minute!");
        log("4. Click 'Check State' to see current status");
        log("5. Test notifications manually");
        log("6. Click 'Cleanup' when done");
        log("");
    }
    
    private void log(String message) {
        logBuilder.append(message).append("\n");
        runOnUiThread(() -> {
            tvLog.setText(logBuilder.toString());
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
        Log.d(TAG, message);
    }
    
    private void runWorkflowTest() {
        log("");
        log("═══════════════════════════════════════");
        log("🚀 STARTING WORKFLOW TEST");
        log("═══════════════════════════════════════");
        log("");
        
        testUserIds = new ArrayList<>();
        
        // Step 1: Create test event
        log("📅 Step 1: Creating test event...");
        createTestEvent();
    }
    
    private void createTestEvent() {
        long now = System.currentTimeMillis();
        long registrationEnd = now + (15 * 1000); // 15 seconds from now (fast testing!)
        long deadlineToAccept = registrationEnd + (4 * 60 * 1000); // 4 minutes after registration ends
        long eventStart = now + (10 * 60 * 1000); // 10 minutes from now (gives time for testing)
        
        testEventId = "TEST_EVENT_" + UUID.randomUUID().toString().substring(0, 8);
        
        Map<String, Object> event = new HashMap<>();
        event.put("title", "Test Event - Workflow");
        event.put("description", "Automated test event");
        event.put("organizerId", organizerId);
        event.put("capacity", 4);
        event.put("sampleSize", 2); // Select 2 out of 4
        event.put("registrationEnd", registrationEnd);
        event.put("deadlineEpochMs", deadlineToAccept);
        event.put("startsAtEpochMs", eventStart);
        event.put("waitlistCount", 0);
        event.put("selectionProcessed", false);
        event.put("selectionNotificationSent", false);
        event.put("deadlineNotificationSent", false);
        event.put("sorryNotificationSent", false);
        event.put("createdAt", now);
        event.put("geolocationRequired", false);
        
        db.collection("events").document(testEventId).set(event)
                .addOnSuccessListener(aVoid -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    SimpleDateFormat sdfLong = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    
                    log("✅ Event created: " + testEventId);
                    log("");
                    log("═══════════════════════════════════════");
                    log("⏰ COMPLETE TIMELINE");
                    log("═══════════════════════════════════════");
                    log("");
                    
                    log("📍 T+0:00  (NOW)");
                    log("   " + sdf.format(new Date(now)));
                    log("   🚀 Test started");
                    log("   ✅ Event created");
                    log("   ✅ 4 users added to waitlist");
                    log("");
                    
                    log("📍 T+0:15  (Registration Deadline) ⚡ FAST!");
                    log("   " + sdf.format(new Date(registrationEnd)));
                    log("   ⏰ Registration period ends (15 seconds!)");
                    log("   🤖 Automatic selection triggers (within 1 min)");
                    log("");
                    
                    log("📍 T+1:15  (Selection Complete)");
                    log("   ~" + sdf.format(new Date(registrationEnd + 60000)));
                    log("   ✅ 2 users selected randomly");
                    log("   📨 Invitations created (status: PENDING)");
                    log("   🔔 Notifications sent to selected users");
                    log("");
                    
                    log("📍 T+4:15  (Automatic Deadline)");
                    log("   " + sdf.format(new Date(deadlineToAccept)));
                    log("   ⏰ Deadline to accept/decline (4 min after reg)");
                    log("   ⚠️  Non-responders → CancelledEntrants");
                    log("");
                    
                    log("📍 T+5:00  (Manual Replacement - Example)");
                    log("   ~" + sdf.format(new Date(now + 5 * 60 * 1000)));
                    log("   🔄 Organizer does manual replacement");
                    log("   ⏱️  Sets deadline: 2 min from now");
                    log("   📨 New invitation created");
                    log("   🔔 Notification sent to replaced user");
                    log("");
                    
                    log("📍 T+9:00  (Manual Deadline & Sorry)");
                    log("   " + sdf.format(new Date(eventStart - 60000)));
                    log("   ⏰ Manual replacement deadline expires (2 min)");
                    log("   📢 Sorry notification sent to NonSelectedEntrants");
                    log("");
                    
                    log("📍 T+10:00 (Event Start)");
                    log("   " + sdf.format(new Date(eventStart)));
                    log("   🎉 Event begins");
                    log("   ✅ Testing complete!");
                    log("");
                    log("═══════════════════════════════════════");
                    log("");
                    
                    // Step 2: Find existing users
                    log("👥 Step 2: Finding 4 existing users...");
                    createTestUsers();
                })
                .addOnFailureListener(e -> {
                    log("❌ Failed to create event: " + e.getMessage());
                });
    }
    
    private void createTestUsers() {
        // Use existing users instead of creating new ones
        String[] testEmails = {
            "shinchan@gmail.com",
            "himawari@gmail.com", 
            "sanika1234@gmail.com",
            "chotabheem@gmail.com"
        };
        
        log("   🔍 Looking up existing users by email...");
        
        // Query Firestore for users with these emails
        List<com.google.android.gms.tasks.Task<?>> tasks = new ArrayList<>();
        
        for (int i = 0; i < testEmails.length; i++) {
            final String email = testEmails[i];
            final int userNum = i + 1;
            
            com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> task = 
                db.collection("users")
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                            String userId = userDoc.getId();
                            testUserIds.add(userId);
                            
                            String firstName = userDoc.getString("firstName");
                            log("   ✅ User " + userNum + " found: " + firstName + " (" + email + ")");
                            log("      UID: " + userId.substring(0, 20) + "...");
                        } else {
                            log("   ❌ User not found: " + email);
                            log("      Please make sure this user exists in Firebase");
                        }
                    })
                    .addOnFailureListener(e -> {
                        log("   ❌ Failed to find user " + email + ": " + e.getMessage());
                    });
            
            tasks.add(task);
        }
        
        // Wait for all queries to complete
        com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                .addOnSuccessListener(taskResults -> {
                    if (testUserIds.size() == 4) {
                        log("");
                        log("✅ All 4 users found!");
                        log("");
                        log("📝 Step 3: Adding users to waitlist...");
                        addUsersToWaitlist();
                    } else {
                        log("");
                        log("❌ Only found " + testUserIds.size() + "/4 users");
                        log("   Make sure all users exist in Firebase:");
                        for (String email : testEmails) {
                            log("   - " + email);
                        }
                        log("");
                        log("💡 Tip: Sign in with each user account in the app first");
                    }
                })
                .addOnFailureListener(e -> {
                    log("❌ Failed to query users: " + e.getMessage());
                });
    }
    
    private void addUsersToWaitlist() {
        long now = System.currentTimeMillis();
        
        for (int i = 0; i < testUserIds.size(); i++) {
            String userId = testUserIds.get(i);
            
            Map<String, Object> entrant = new HashMap<>();
            entrant.put("userId", userId);
            entrant.put("joinedAt", now);
            
            final int userNum = i + 1;
            db.collection("events").document(testEventId)
                    .collection("WaitlistedEntrants")
                    .document(userId)
                    .set(entrant)
                    .addOnSuccessListener(aVoid -> {
                        log("   ✅ User " + userNum + " added to waitlist");
                        
                        // Update waitlist count
                        db.collection("events").document(testEventId)
                                .update("waitlistCount", userNum);
                        
                        // If last user, show next steps
                        if (userNum == 4) {
                            log("");
                            log("✅ All users added to waitlist!");
                            log("");
                            log("═══════════════════════════════════════");
                            log("📋 TESTING CHECKLIST (FAST MODE ⚡)");
                            log("═══════════════════════════════════════");
                            log("");
                            
                            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                            long checklistTime = System.currentTimeMillis();
                            
                            log("⏳ [T+0:15] " + sdf.format(new Date(checklistTime + 15 * 1000)) + " ⚡");
                            log("   □ Wait ~15 seconds for registration to end");
                            log("   □ Automatic selection triggers immediately!");
                            log("");
                            
                            log("✅ [T+1:15] " + sdf.format(new Date(checklistTime + 75 * 1000)));
                            log("   □ Click 'Check State' to verify 2 selected");
                            log("   □ Check notifications on selected users' devices");
                            log("   □ Click notification → opens event detail");
                            log("   □ Verify Accept/Decline buttons show");
                            log("");
                            
                            log("📱 [T+1-4:15] Test acceptance window");
                            log("   □ Test accepting invitation");
                            log("   □ Test declining invitation");
                            log("   □ Verify user moves to correct collection");
                            log("");
                            
                            log("⏰ [T+4:15] " + sdf.format(new Date(checklistTime + 255 * 1000)));
                            log("   □ Automatic deadline expires (4 min after reg)");
                            log("   □ Non-responders → CancelledEntrants");
                            log("");
                            
                            log("🔄 [T+5:00] " + sdf.format(new Date(checklistTime + 5 * 60 * 1000)));
                            log("   □ Go to OrganizerViewEntrantsActivity");
                            log("   □ Click 'Replacement' button");
                            log("   □ Select from NonSelectedEntrants");
                            log("   □ Set deadline: 2 minutes");
                            log("   □ Verify notification sent");
                            log("");
                            
                            log("📢 [T+9:00] " + sdf.format(new Date(checklistTime + 9 * 60 * 1000)));
                            log("   □ Sorry notification sent");
                            log("   □ Check NonSelectedEntrants received it");
                            log("");
                            
                            log("🎉 [T+10:00] " + sdf.format(new Date(checklistTime + 10 * 60 * 1000)));
                            log("   □ Event starts");
                            log("   □ Click 'Cleanup' to remove test data");
                            log("");
                            log("═══════════════════════════════════════");
                            log("");
                            log("🔍 Monitor Progress:");
                            log("   • Click 'Check State' anytime");
                            log("   • Watch Firebase Console real-time:");
                            log("     - events/" + testEventId + "/SelectedEntrants");
                            log("     - events/" + testEventId + "/NonSelectedEntrants");
                            log("     - invitations (filter by eventId)");
                            log("     - notificationRequests (filter by eventId)");
                            log("");
                        }
                    })
                    .addOnFailureListener(e -> {
                        log("   ❌ Failed to add user " + userNum + " to waitlist: " + e.getMessage());
                    });
        }
    }
    
    private void checkCurrentState() {
        log("");
        log("═══════════════════════════════════════");
        log("🔍 CHECKING CURRENT STATE");
        log("═══════════════════════════════════════");
        log("");
        
        if (testEventId == null) {
            log("❌ No test event found. Click 'Start Test' first.");
            return;
        }
        
        // Check event state
        db.collection("events").document(testEventId).get()
                .addOnSuccessListener(eventDoc -> {
                    if (!eventDoc.exists()) {
                        log("❌ Event not found!");
                        return;
                    }
                    
                    Boolean selectionProcessed = eventDoc.getBoolean("selectionProcessed");
                    Boolean selectionNotificationSent = eventDoc.getBoolean("selectionNotificationSent");
                    Long registrationEnd = eventDoc.getLong("registrationEnd");
                    Long now = System.currentTimeMillis();
                    
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    
                    log("📅 Event: " + testEventId);
                    log("   ⏰ Current time: " + sdf.format(new Date(now)));
                    log("   ⏰ Registration ends: " + sdf.format(new Date(registrationEnd)));
                    log("   📊 Selection processed: " + selectionProcessed);
                    log("   📨 Notification sent: " + selectionNotificationSent);
                    log("");
                    
                    // Check each collection
                    checkCollection("WaitlistedEntrants");
                    checkCollection("SelectedEntrants");
                    checkCollection("NonSelectedEntrants");
                    checkCollection("CancelledEntrants");
                    
                    // Check invitations
                    db.collection("invitations")
                            .whereEqualTo("eventId", testEventId)
                            .get()
                            .addOnSuccessListener(invSnapshot -> {
                                log("");
                                log("💌 Invitations: " + invSnapshot.size());
                                for (DocumentSnapshot inv : invSnapshot.getDocuments()) {
                                    String status = inv.getString("status");
                                    String uid = inv.getString("uid");
                                    log("   - " + uid.substring(0, 15) + "... → " + status);
                                }
                            });
                    
                    // Check notification requests
                    db.collection("notificationRequests")
                            .whereEqualTo("eventId", testEventId)
                            .get()
                            .addOnSuccessListener(notifSnapshot -> {
                                log("");
                                log("📬 Notification Requests: " + notifSnapshot.size());
                                for (DocumentSnapshot notif : notifSnapshot.getDocuments()) {
                                    String title = notif.getString("title");
                                    Boolean processed = notif.getBoolean("processed");
                                    Long sentCount = notif.getLong("sentCount");
                                    log("   - " + title);
                                    log("     Processed: " + processed + ", Sent: " + sentCount);
                                }
                                log("");
                            });
                })
                .addOnFailureListener(e -> {
                    log("❌ Failed to check state: " + e.getMessage());
                });
    }
    
    private void checkCollection(String collectionName) {
        db.collection("events").document(testEventId)
                .collection(collectionName)
                .get()
                .addOnSuccessListener(snapshot -> {
                    log("   " + collectionName + ": " + snapshot.size());
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String userId = doc.getId();
                        // Find user number
                        int userNum = 0;
                        for (int i = 0; i < testUserIds.size(); i++) {
                            if (testUserIds.get(i).equals(userId)) {
                                userNum = i + 1;
                                break;
                            }
                        }
                        log("     - User " + userNum + " (" + userId.substring(0, 15) + "...)");
                    }
                });
    }
    
    private void cleanupTestData() {
        log("");
        log("═══════════════════════════════════════");
        log("🧹 CLEANING UP TEST DATA");
        log("═══════════════════════════════════════");
        log("");
        
        if (testEventId == null) {
            log("✅ No test data to clean up");
            return;
        }
        
        // Delete event and subcollections
        log("🗑️ Deleting event: " + testEventId);
        
        String[] subcollections = {"WaitlistedEntrants", "SelectedEntrants", "NonSelectedEntrants", "CancelledEntrants"};
        
        for (String subcol : subcollections) {
            db.collection("events").document(testEventId).collection(subcol).get()
                    .addOnSuccessListener(snapshot -> {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            doc.getReference().delete();
                        }
                        log("   ✅ Deleted " + subcol);
                    });
        }
        
        // Delete event
        db.collection("events").document(testEventId).delete()
                .addOnSuccessListener(aVoid -> {
                    log("   ✅ Event deleted");
                });
        
        // DON'T delete real users (they're not test users)
        log("   ℹ️  Skipping user deletion (using real users, not test users)");
        
        // Delete invitations
        db.collection("invitations")
                .whereEqualTo("eventId", testEventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        doc.getReference().delete();
                    }
                    log("   ✅ Invitations deleted");
                });
        
        // Delete notification requests
        db.collection("notificationRequests")
                .whereEqualTo("eventId", testEventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        doc.getReference().delete();
                    }
                    log("   ✅ Notification requests deleted");
                    log("");
                    log("✅ Cleanup complete!");
                });
        
        testEventId = null;
        testUserIds = null;
    }
}

