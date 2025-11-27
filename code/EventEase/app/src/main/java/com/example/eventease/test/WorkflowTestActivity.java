package com.example.eventease.test;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.R;
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
 * 
 * ⚠️ IMPORTANT: With Device ID authentication, this test now requires:
 * - 4 Android emulators (1 organizer + 3 entrants)
 * - Manual testing by switching between emulators
 * - Each emulator will have a unique device ID
 * 
 * The automated email-based testing is NO LONGER SUPPORTED.
 * See MIGRATION_FINAL_SUMMARY.md for emulator setup instructions.
 */
public class WorkflowTestActivity extends AppCompatActivity {
    private static final String TAG = "WorkflowTest";
    
    private TextView tvLog;
    private ScrollView scrollView;
    private StringBuilder logBuilder;
    private FirebaseFirestore db;
    private Button btnOpenOrganizerView;
    
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
        btnOpenOrganizerView = findViewById(R.id.btnOpenOrganizerView);
        
        logBuilder = new StringBuilder();
        db = FirebaseFirestore.getInstance();
        
        // Get device ID as organizer ID
        organizerId = com.example.eventease.auth.AuthHelper.getUid(this);
        
        btnStart.setOnClickListener(v -> runWorkflowTest());
        btnCheckState.setOnClickListener(v -> checkCurrentState());
        btnCleanup.setOnClickListener(v -> cleanupTestData());
        btnOpenOrganizerView.setOnClickListener(v -> openOrganizerView());
        
        // Check if there's an existing test event
        if (testEventId != null && !testEventId.isEmpty()) {
            btnOpenOrganizerView.setEnabled(true);
        }
        
        log("🎬 Workflow Test Ready!");
        log("📱 Organizer Device ID: " + organizerId);
        log("");
        log("⚠️ DEVICE ID AUTHENTICATION MODE");
        log("════════════════════════════════");
        log("This test NOW REQUIRES 4 emulators!");
        log("Each emulator = unique device ID");
        log("No email/password login needed!");
        log("");
        log("═══════════════════════════════════════");
        log("⚡ MULTI-ACCOUNT TESTING WORKFLOW");
        log("═══════════════════════════════════════");
        log("");
        log("PHASE 1: Setup (as Organizer)");
        log("  1. Click 'Start Test' → creates event");
        log("  2. Wait 15 seconds → selection happens");
        log("  3. Click 'Check State' → see who's selected");
        log("");
        log("PHASE 2: Accept/Decline (Switch to Entrant Emulator)");
        log("  1. Switch to selected user's emulator");
        log("  2. Check notification → accept or decline");
        log("  3. Test stays active - persistent!");
        log("");
        log("PHASE 3: Replacement (Switch to Organizer Emulator)");
        log("  1. Switch back to organizer emulator");
        log("  2. Click '🎯 Open Organizer View' button");
        log("  4. Click 'Replacement' → select from NonSelected");
        log("  5. Provide deadline → send invitation");
        log("");
        log("💡 TIP: Test event persists across logins!");
        log("You can switch accounts and come back anytime.");
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
                    
                    // Enable the organizer view button
                    if (btnOpenOrganizerView != null) {
                        btnOpenOrganizerView.setEnabled(true);
                    }
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
        log("   📊 Querying: users collection, field: 'email'");
        log("");
        
        // First, let's check if there are ANY users in the collection
        db.collection("users").limit(3).get()
                .addOnSuccessListener(snapshot -> {
                    log("   📋 Sample of users in Firestore:");
                    if (snapshot.isEmpty()) {
                        log("   ⚠️  WARNING: 'users' collection is EMPTY!");
                        log("   Please sign in with at least one account first.");
                    } else {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String email = doc.getString("email");
                            String firstName = doc.getString("firstName");
                            log("   - " + firstName + " (" + email + ")");
                        }
                    }
                    log("");
                    
                    // Now query for our specific test users
                    queryTestUsers(testEmails);
                })
                .addOnFailureListener(e -> {
                    log("   ❌ Failed to check users collection: " + e.getMessage());
                    log("");
                    // Still try to query for test users
                    queryTestUsers(testEmails);
                });
    }
    
    private void queryTestUsers(String[] testEmails) {
        // Query Firestore for users with these emails
        List<com.google.android.gms.tasks.Task<?>> tasks = new ArrayList<>();
        List<String> foundUserIds = new ArrayList<>();
        List<String> notFoundEmails = new ArrayList<>();
        
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
                            foundUserIds.add(userId);
                            testUserIds.add(userId);
                            
                            String firstName = userDoc.getString("firstName");
                            String fcmToken = userDoc.getString("fcmToken");
                            log("   ✅ User " + userNum + " found!");
                            log("      Name: " + firstName);
                            log("      Email: " + email);
                            log("      UID: " + userId.substring(0, Math.min(20, userId.length())) + "...");
                            log("      FCM Token: " + (fcmToken != null ? "✓ Present" : "✗ Missing"));
                        } else {
                            notFoundEmails.add(email);
                            log("   ❌ User " + userNum + " NOT FOUND: " + email);
                        }
                    })
                    .addOnFailureListener(e -> {
                        notFoundEmails.add(email);
                        log("   ❌ Error finding user " + userNum + " (" + email + "): " + e.getMessage());
                    });
            
            tasks.add(task);
        }
        
        // Wait for all queries to complete
        com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                .addOnSuccessListener(taskResults -> {
                    log("");
                    log("═══════════════════════════════════════");
                    log("📊 USER LOOKUP RESULTS");
                    log("═══════════════════════════════════════");
                    log("   Found: " + foundUserIds.size() + "/4 users");
                    log("   Missing: " + notFoundEmails.size() + " users");
                    log("");
                    
                    if (testUserIds.size() == 4) {
                        log("✅ All 4 users found! Proceeding...");
                        log("");
                        log("📝 Step 3: Adding users to waitlist...");
                        addUsersToWaitlist();
                    } else {
                        log("❌ Cannot proceed - missing users!");
                        log("");
                        log("🔍 Troubleshooting:");
                        log("");
                        log("1. Missing users:");
                        for (String email : notFoundEmails) {
                            log("   ✗ " + email);
                        }
                        log("");
                        log("2. Sign in to the app with each account:");
                        log("   • Open EventEase app");
                        log("   • Sign in with the missing email");
                        log("   • This creates the user in Firestore");
                        log("");
                        log("3. Check Firebase Console:");
                        log("   • Go to Firestore Database");
                        log("   • Check 'users' collection");
                        log("   • Look for documents with 'email' field");
                        log("");
                        log("4. Try again after signing in");
                    }
                })
                .addOnFailureListener(e -> {
                    log("");
                    log("❌ Failed to query users: " + e.getMessage());
                    log("   Error type: " + e.getClass().getSimpleName());
                });
    }
    
    private void addUsersToWaitlist() {
        long now = System.currentTimeMillis();
        
        for (int i = 0; i < testUserIds.size(); i++) {
            String userId = testUserIds.get(i);
            final int userNum = i + 1;
            
            // Fetch user data to include name information
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(userDoc -> {
                        if (!userDoc.exists()) {
                            log("   ❌ User " + userNum + " document not found in users collection!");
                            return;
                        }
                        
                        // Build entrant data with user information (includes names)
                        Map<String, Object> entrant = new HashMap<>();
                        entrant.put("userId", userId);
                        entrant.put("joinedAt", now);
                        
                        // Get all available fields from user document
                        String firstName = userDoc.getString("firstName");
                        String lastName = userDoc.getString("lastName");
                        String fullName = userDoc.getString("fullName");
                        String name = userDoc.getString("name");
                        String displayName = userDoc.getString("displayName");
                        String email = userDoc.getString("email");
                        String phoneNumber = userDoc.getString("phoneNumber");
                        
                        // ALWAYS add email if it exists
                        if (email != null && !email.isEmpty()) {
                            entrant.put("email", email);
                        }
                        
                        // Add name fields if they exist
                        if (firstName != null && !firstName.isEmpty()) {
                            entrant.put("firstName", firstName);
                        }
                        if (lastName != null && !lastName.isEmpty()) {
                            entrant.put("lastName", lastName);
                        }
                        if (fullName != null && !fullName.isEmpty()) {
                            entrant.put("fullName", fullName);
                        }
                        if (name != null && !name.isEmpty()) {
                            entrant.put("name", name);
                        }
                        if (phoneNumber != null && !phoneNumber.isEmpty()) {
                            entrant.put("phoneNumber", phoneNumber);
                        }
                        
                        // Build displayName (priority: displayName > fullName > firstName+lastName > name > email)
                        if (displayName == null || displayName.isEmpty()) {
                            if (fullName != null && !fullName.isEmpty()) {
                                displayName = fullName;
                            } else if (firstName != null && !firstName.isEmpty()) {
                                displayName = firstName + (lastName != null && !lastName.isEmpty() ? " " + lastName : "");
                            } else if (name != null && !name.isEmpty()) {
                                displayName = name;
                            } else if (email != null && !email.isEmpty()) {
                                // Fallback: use email prefix as display name
                                displayName = email.split("@")[0];
                            } else {
                                displayName = "User " + userNum;
                            }
                        }
                        
                        // ALWAYS add displayName
                        entrant.put("displayName", displayName);
                        
                        final String finalDisplayName = displayName;
                        
                        db.collection("events").document(testEventId)
                                .collection("WaitlistedEntrants")
                                .document(userId)
                                .set(entrant)
                                .addOnSuccessListener(aVoid -> {
                                    log("   ✅ User " + userNum + " added to waitlist: " + finalDisplayName);
                                    
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
                })
                .addOnFailureListener(e -> {
                    log("   ❌ Failed to fetch user " + userNum + " data: " + e.getMessage());
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
                        String firstName = doc.getString("firstName");
                        String lastName = doc.getString("lastName");
                        String displayName = doc.getString("displayName");
                        
                        // Build display string
                        String nameStr = displayName;
                        if (nameStr == null || nameStr.isEmpty()) {
                            nameStr = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
                            nameStr = nameStr.trim();
                        }
                        if (nameStr.isEmpty()) {
                            nameStr = "Unknown";
                        }
                        
                        // Find user number
                        int userNum = 0;
                        for (int i = 0; i < testUserIds.size(); i++) {
                            if (testUserIds.get(i).equals(userId)) {
                                userNum = i + 1;
                                break;
                            }
                        }
                        log("     - " + nameStr + " (User " + userNum + ")");
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
    
    private void openOrganizerView() {
        if (testEventId == null || testEventId.isEmpty()) {
            log("❌ No test event available. Start test first!");
            return;
        }
        
        log("");
        log("═══════════════════════════════════════");
        log("🎯 Opening Organizer View for Replacement");
        log("═══════════════════════════════════════");
        log("");
        log("Event ID: " + testEventId);
        log("");
        log("Opening OrganizerViewEntrantsActivity...");
        log("");
        log("In the opened activity:");
        log("  1. Check 'NonSelectedEntrants' tab");
        log("  2. Click 'Replacement' button");
        log("  3. Select how many to replace");
        log("  4. Provide deadline (before event start)");
        log("  5. Notification sent automatically!");
        log("");
        
        // Open OrganizerViewEntrantsActivity with the test event
        android.content.Intent intent = new android.content.Intent(
            this, 
            com.example.eventease.ui.organizer.OrganizerViewEntrantsActivity.class
        );
        intent.putExtra("eventId", testEventId);
        intent.putExtra("eventTitle", "Test Event - Workflow");
        startActivity(intent);
    }
}

