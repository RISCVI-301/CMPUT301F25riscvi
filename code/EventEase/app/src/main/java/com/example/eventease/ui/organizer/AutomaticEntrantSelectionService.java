package com.example.eventease.ui.organizer;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service that automatically processes entrant selection when event registration periods end.
 * 
 * <p>This service automatically checks for events whose registration period has ended
 * and triggers the selection process to randomly select entrants from the waitlist.
 * It runs as a background listener to ensure selections happen automatically when
 * registration periods end, even when the app is not actively being used.
 * 
 * <p>The service selects entrants (not events) - it picks random entrants from
 * the waitlist for events that have reached their registration deadline.
 */
public class AutomaticEntrantSelectionService extends JobService {
    private static final String TAG = "AutoEntrantSelection";
    private static final int JOB_ID = 1001;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final EventSelectionHelper selectionHelper = new EventSelectionHelper();

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Starting automatic entrant selection check");
        
        executor.execute(() -> {
            try {
                checkAndProcessEvents();
            } catch (Exception e) {
                Log.e(TAG, "Error in automatic entrant selection", e);
            } finally {
                jobFinished(params, false); // Don't reschedule - we'll use listeners instead
            }
        });
        
        return true; // Job is running asynchronously
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Stopping automatic entrant selection check");
        return false; // Don't reschedule
    }

    /**
     * Checks all events and processes entrant selection for those whose registration period has ended.
     * This selects entrants from the waitlist, not the events themselves.
     */
    private void checkAndProcessEvents() {
        Log.d(TAG, "Querying events for automatic entrant selection processing");
        
        Query query = db.collection("events")
                .whereGreaterThan("registrationEnd", 0L)
                .whereEqualTo("selectionProcessed", false);
        
        query.get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        Log.d(TAG, "No events found that need selection processing");
                        return;
                    }
                    
                    List<DocumentSnapshot> events = snapshot.getDocuments();
                    Log.d(TAG, "Found " + events.size() + " events to check for selection");
                    
                    long currentTime = System.currentTimeMillis();
                    int processedCount = 0;
                    
                    for (DocumentSnapshot eventDoc : events) {
                        Long registrationEnd = eventDoc.getLong("registrationEnd");
                        if (registrationEnd != null && registrationEnd > 0 && currentTime >= registrationEnd) {
                            String eventId = eventDoc.getId();
                            Log.d(TAG, "Processing entrant selection for event: " + eventId);
                            
                            selectionHelper.checkAndProcessEventSelection(eventId, new EventSelectionHelper.SelectionCallback() {
                                @Override
                                public void onComplete(int selectedCount) {
                                    if (selectedCount > 0) {
                                        Log.d(TAG, "Successfully selected " + selectedCount + " entrants for event " + eventId);
                                    }
                                }
                                
                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "Error selecting entrants for event " + eventId + ": " + error);
                                }
                            });
                            
                            processedCount++;
                        }
                    }
                    
                    Log.d(TAG, "Processed " + processedCount + " events for automatic entrant selection");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to query events for automatic selection", e);
                });
    }

    private static com.google.firebase.firestore.ListenerRegistration selectionListenerRegistration;
    private static final java.util.Set<String> processingEventIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean isInitialLoad = true;
    private static long listenerSetupTime = 0;
    private static final long INITIAL_LOAD_SKIP_DURATION_MS = 10 * 1000; // Skip processing for 10 seconds after listener setup
    private static final long PERIODIC_CHECK_INTERVAL_MS = 60 * 1000; // Check every 60 seconds for events that need processing
    private static android.os.Handler periodicCheckHandler;
    private static java.lang.Runnable periodicCheckRunnable;
    
    /**
     * Sets up a Firestore listener to automatically select entrants when event registration periods end.
     * This is more efficient than polling and ensures immediate processing.
     * 
     * <p>When an event's registration period ends, this automatically:
     * <ol>
     *   <li>Selects random entrants from the waitlist</li>
     *   <li>Sends invitations to selected entrants</li>
     *   <li>Sends push notifications</li>
     * </ol>
     * 
     * <p>This method sets up both:
     * <ul>
     *   <li>A real-time listener for immediate processing when events change</li>
     *   <li>A periodic check to catch events that need processing but weren't caught by the listener</li>
     * </ul>
     */
    public static void setupAutomaticSelectionListener() {
        // Remove existing listener if any to prevent duplicates
        if (selectionListenerRegistration != null) {
            Log.d(TAG, "Removing existing automatic selection listener to prevent duplicates");
            selectionListenerRegistration.remove();
            selectionListenerRegistration = null;
        }
        
        // Stop existing periodic check if any
        if (periodicCheckHandler != null && periodicCheckRunnable != null) {
            periodicCheckHandler.removeCallbacks(periodicCheckRunnable);
        }
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        EventSelectionHelper selectionHelper = new EventSelectionHelper();
        
        Log.d(TAG, "Setting up automatic entrant selection listener");
        Log.d(TAG, "Listener setup time: " + new java.util.Date(System.currentTimeMillis()));
        
        // Record when listener is set up to skip processing for a short period
        listenerSetupTime = System.currentTimeMillis();
        isInitialLoad = true; // Reset flag when setting up new listener
        
        // Set up periodic check to catch events that need processing
        periodicCheckHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        periodicCheckRunnable = new java.lang.Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Running periodic check for events that need selection processing");
                checkAndProcessPendingEvents(selectionHelper);
                // Schedule next check
                if (periodicCheckHandler != null && periodicCheckRunnable != null) {
                    periodicCheckHandler.postDelayed(periodicCheckRunnable, PERIODIC_CHECK_INTERVAL_MS);
                }
            }
        };
        // Start periodic check immediately and then every interval
        periodicCheckHandler.post(periodicCheckRunnable);
        
        // Listen to events where registrationEnd is in the past and selectionProcessed is false
        // Only process document changes to avoid processing all events on every snapshot
        selectionListenerRegistration = db.collection("events")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error in automatic entrant selection listener", error);
                        return;
                    }
                    
                    if (snapshot == null) {
                        return;
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    
                    // Only process changed documents (ADDED or MODIFIED)
                    // This prevents processing all events on every snapshot
                    Log.d(TAG, "Snapshot received with " + snapshot.getDocumentChanges().size() + " document changes");
                    for (com.google.firebase.firestore.DocumentChange change : snapshot.getDocumentChanges()) {
                        DocumentSnapshot eventDoc = change.getDocument();
                        Long registrationEnd = eventDoc.getLong("registrationEnd");
                        Boolean selectionProcessed = eventDoc.getBoolean("selectionProcessed");
                        Boolean selectionNotificationSent = eventDoc.getBoolean("selectionNotificationSent");
                        String eventId = eventDoc.getId();
                        
                        Log.d(TAG, "Processing change type: " + change.getType() + " for event: " + eventId + 
                            ", registrationEnd: " + (registrationEnd != null ? new java.util.Date(registrationEnd) : "null") +
                            ", selectionProcessed: " + selectionProcessed +
                            ", selectionNotificationSent: " + selectionNotificationSent);
                        
                        // Only process if document was added or modified
                        if (change.getType() != com.google.firebase.firestore.DocumentChange.Type.ADDED && 
                            change.getType() != com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                            continue;
                        }
                        
                        // Skip if selection notification already sent (prevents duplicates)
                        if (Boolean.TRUE.equals(selectionNotificationSent)) {
                            Log.d(TAG, "Event " + eventId + " selection notification already sent, skipping");
                            continue;
                        }
                        
                        // Skip if already processing this event
                        if (processingEventIds.contains(eventId)) {
                            Log.d(TAG, "Event " + eventId + " is already being processed, skipping");
                            continue;
                        }
                        
                        // Skip if already processed
                        if (Boolean.TRUE.equals(selectionProcessed)) {
                            continue;
                        }
                        
                        // Skip if event start date has already passed
                        Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
                        if (startsAtEpochMs != null && startsAtEpochMs > 0 && currentTime >= startsAtEpochMs) {
                            Log.d(TAG, "Event " + eventId + " start date has already passed, skipping selection");
                            continue;
                        }
                        
                        // Check if registration period has ended
                        // For newly created events, check if registrationEnd is in the past
                        // For existing events, only process if they were just modified and registrationEnd just passed
                        if (registrationEnd != null && registrationEnd > 0) {
                            // Check if registration period has ended
                            long timeUntilRegistrationEnd = registrationEnd - currentTime;
                            Log.d(TAG, "Event " + eventId + " - Time until registration end: " + 
                                (timeUntilRegistrationEnd / 1000) + " seconds");
                            
                            if (currentTime >= registrationEnd) {
                                // For initial load, skip events that were created before listener setup
                                // This prevents processing old events when app starts
                                if (isInitialLoad) {
                                    // Check if event was created before listener setup (more than 5 seconds ago)
                                    Long createdAt = eventDoc.getLong("createdAt");
                                    if (createdAt == null || createdAt == 0) {
                                        // No createdAt field, check if registrationEnd is old (more than 5 seconds ago)
                                        if ((currentTime - registrationEnd) > 5000) {
                                            Log.d(TAG, "Skipping old event " + eventId + " on initial load");
                                            continue;
                                        }
                                    } else if (listenerSetupTime > 0 && (createdAt < (listenerSetupTime - 5000))) {
                                        Log.d(TAG, "Skipping old event " + eventId + " created before listener setup");
                                        continue;
                                    }
                                }
                                
                                // Mark as processing to prevent duplicate processing
                                processingEventIds.add(eventId);
                                Log.d(TAG, "Auto-processing entrant selection for event: " + eventId + 
                                    " (registration ended at " + new java.util.Date(registrationEnd) + ")");
                                
                                selectionHelper.checkAndProcessEventSelection(eventId, new EventSelectionHelper.SelectionCallback() {
                                    @Override
                                    public void onComplete(int selectedCount) {
                                        // Remove from processing set
                                        processingEventIds.remove(eventId);
                                        if (selectedCount > 0) {
                                            Log.d(TAG, "Auto-selection completed: " + selectedCount + " entrants selected for event " + eventId);
                                        }
                                    }
                                    
                                    @Override
                                    public void onError(String error) {
                                        // Remove from processing set even on error
                                        processingEventIds.remove(eventId);
                                        Log.e(TAG, "Auto-selection error for event " + eventId + ": " + error);
                                    }
                                });
                            }
                        }
                    }
                    
                    // Mark initial load as complete after processing first batch
                    if (isInitialLoad) {
                        isInitialLoad = false;
                        Log.d(TAG, "Initial load complete, will process new events normally");
                    }
                });
    }
    
    /**
     * Periodically checks for events that need selection processing.
     * This catches events whose registration period ended but weren't caught by the real-time listener.
     */
    private static void checkAndProcessPendingEvents(EventSelectionHelper selectionHelper) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        long currentTime = System.currentTimeMillis();
        
        // Query for events that:
        // 1. Have a registrationEnd time
        // 2. Haven't been processed yet (selectionProcessed = false)
        // 3. Registration period has ended (currentTime >= registrationEnd)
        // 4. Event hasn't started yet (currentTime < startsAtEpochMs)
        Query query = db.collection("events")
                .whereGreaterThan("registrationEnd", 0L)
                .whereEqualTo("selectionProcessed", false);
        
        query.get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        Log.d(TAG, "Periodic check: No events found that need selection processing");
                        return;
                    }
                    
                    List<DocumentSnapshot> events = snapshot.getDocuments();
                    Log.d(TAG, "Periodic check: Found " + events.size() + " events to check for selection");
                    
                    int processedCount = 0;
                    
                    for (DocumentSnapshot eventDoc : events) {
                        Long registrationEnd = eventDoc.getLong("registrationEnd");
                        Boolean selectionProcessed = eventDoc.getBoolean("selectionProcessed");
                        Boolean selectionNotificationSent = eventDoc.getBoolean("selectionNotificationSent");
                        String eventId = eventDoc.getId();
                        
                        // Skip if already processed
                        if (Boolean.TRUE.equals(selectionProcessed)) {
                            continue;
                        }
                        
                        // Skip if selection notification already sent
                        if (Boolean.TRUE.equals(selectionNotificationSent)) {
                            continue;
                        }
                        
                        // Skip if already processing this event
                        if (processingEventIds.contains(eventId)) {
                            continue;
                        }
                        
                        // Skip if event start date has already passed
                        Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
                        if (startsAtEpochMs != null && startsAtEpochMs > 0 && currentTime >= startsAtEpochMs) {
                            continue;
                        }
                        
                        // Check if registration period has ended
                        if (registrationEnd != null && registrationEnd > 0 && currentTime >= registrationEnd) {
                            // Mark as processing to prevent duplicate processing
                            processingEventIds.add(eventId);
                            Log.d(TAG, "Periodic check: Processing entrant selection for event: " + eventId + 
                                " (registration ended at " + new java.util.Date(registrationEnd) + ")");
                            
                            selectionHelper.checkAndProcessEventSelection(eventId, new EventSelectionHelper.SelectionCallback() {
                                @Override
                                public void onComplete(int selectedCount) {
                                    // Remove from processing set
                                    processingEventIds.remove(eventId);
                                    if (selectedCount > 0) {
                                        Log.d(TAG, "Periodic check: Successfully selected " + selectedCount + " entrants for event " + eventId);
                                    }
                                }
                                
                                @Override
                                public void onError(String error) {
                                    // Remove from processing set even on error
                                    processingEventIds.remove(eventId);
                                    Log.e(TAG, "Periodic check: Error selecting entrants for event " + eventId + ": " + error);
                                }
                            });
                            
                            processedCount++;
                        }
                    }
                    
                    if (processedCount > 0) {
                        Log.d(TAG, "Periodic check: Processed " + processedCount + " events for automatic entrant selection");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Periodic check: Failed to query events for automatic selection", e);
                });
    }
    
    /**
     * Stops the automatic selection listener.
     */
    public static void stopAutomaticSelectionListener() {
        if (selectionListenerRegistration != null) {
            selectionListenerRegistration.remove();
            selectionListenerRegistration = null;
        }
        
        // Stop periodic check
        if (periodicCheckHandler != null && periodicCheckRunnable != null) {
            periodicCheckHandler.removeCallbacks(periodicCheckRunnable);
            periodicCheckHandler = null;
            periodicCheckRunnable = null;
        }
        
        processingEventIds.clear();
        isInitialLoad = true;
        Log.d(TAG, "Stopped automatic entrant selection listener and periodic check");
    }
}

