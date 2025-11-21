package com.example.eventease.ui.organizer;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class EventSelectionHelper {
    private static final String TAG = "EventSelectionHelper";
    private final FirebaseFirestore db;
    
    public EventSelectionHelper() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    public interface SelectionCallback {
        void onComplete(int selectedCount);
        void onError(String error);
    }
    
    public void checkAndProcessEventSelection(String eventId, SelectionCallback callback) {
        if (eventId == null || eventId.isEmpty()) {
            if (callback != null) {
                callback.onError("Event ID is required");
            }
            return;
        }
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        
        eventRef.get().addOnSuccessListener(eventDoc -> {
            if (eventDoc == null || !eventDoc.exists()) {
                if (callback != null) {
                    callback.onError("Event not found");
                }
                return;
            }
            
            Long registrationEnd = eventDoc.getLong("registrationEnd");
            Boolean selectionProcessed = eventDoc.getBoolean("selectionProcessed");
            
            if (registrationEnd == null || registrationEnd == 0) {
                Log.d(TAG, "Event " + eventId + " does not have a registration end time");
                if (callback != null) {
                    callback.onComplete(0);
                }
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            if (currentTime < registrationEnd) {
                Log.d(TAG, "Registration deadline has not passed for event " + eventId);
                if (callback != null) {
                    callback.onComplete(0);
                }
                return;
            }
            
            if (Boolean.TRUE.equals(selectionProcessed)) {
                Log.d(TAG, "Selection already processed for event " + eventId);
                if (callback != null) {
                    callback.onComplete(0);
                }
                return;
            }
            
            Integer capacity = eventDoc.getLong("capacity") != null ? 
                eventDoc.getLong("capacity").intValue() : -1;
            
            if (capacity <= 0) {
                Log.d(TAG, "Event " + eventId + " has unlimited or invalid capacity");
                markAsProcessed(eventRef, callback);
                return;
            }
            
            processSelection(eventRef, eventId, capacity, callback);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to load event " + eventId, e);
            if (callback != null) {
                callback.onError("Failed to load event: " + e.getMessage());
            }
        });
    }
    
    private void processSelection(DocumentReference eventRef, String eventId, int capacity, SelectionCallback callback) {
        Log.d(TAG, "Processing selection for event " + eventId + " with capacity " + capacity);
        
        eventRef.collection("WaitlistedEntrants").get()
                .addOnSuccessListener(waitlistSnapshot -> {
                    if (waitlistSnapshot == null || waitlistSnapshot.isEmpty()) {
                        Log.d(TAG, "No waitlisted entrants to select from");
                        markAsProcessed(eventRef, callback);
                        return;
                    }
                    
                    List<DocumentSnapshot> waitlistedDocs = waitlistSnapshot.getDocuments();
                    int availableCount = waitlistedDocs.size();
                    int toSelect = Math.min(capacity, availableCount);
                    
                    if (toSelect == 0) {
                        Log.d(TAG, "No entrants to select");
                        markAsProcessed(eventRef, callback);
                        return;
                    }
                    
                    Log.d(TAG, "Selecting " + toSelect + " out of " + availableCount + " waitlisted entrants");
                    
                    List<DocumentSnapshot> selectedDocs = randomlySelect(waitlistedDocs, toSelect);
                    moveToSelected(eventRef, eventId, selectedDocs, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load waitlisted entrants", e);
                    if (callback != null) {
                        callback.onError("Failed to load waitlisted entrants: " + e.getMessage());
                    }
                });
    }
    
    private List<DocumentSnapshot> randomlySelect(List<DocumentSnapshot> allDocs, int count) {
        List<DocumentSnapshot> shuffled = new ArrayList<>(allDocs);
        Collections.shuffle(shuffled, new Random());
        return shuffled.subList(0, count);
    }
    
    private void moveToSelected(DocumentReference eventRef, String eventId, 
                                List<DocumentSnapshot> selectedDocs, SelectionCallback callback) {
        if (selectedDocs == null || selectedDocs.isEmpty()) {
            markAsProcessed(eventRef, callback);
            return;
        }
        
        WriteBatch batch = db.batch();
        int batchCount = 0;
        final int MAX_BATCH_SIZE = 499;
        List<Task<Void>> batchTasks = new ArrayList<>();
        
        for (DocumentSnapshot doc : selectedDocs) {
            String userId = doc.getId();
            Map<String, Object> data = doc.getData();
            
            if (data == null) {
                continue;
            }
            
            DocumentReference selectedRef = eventRef.collection("SelectedEntrants").document(userId);
            DocumentReference waitlistRef = eventRef.collection("WaitlistedEntrants").document(userId);
            
            batch.set(selectedRef, data);
            batch.delete(waitlistRef);
            batchCount += 2;
            
            if (batchCount >= MAX_BATCH_SIZE) {
                final WriteBatch currentBatch = batch;
                batchTasks.add(currentBatch.commit());
                batch = db.batch();
                batchCount = 0;
            }
        }
        
        batch.update(eventRef, "waitlistCount", 
            com.google.firebase.firestore.FieldValue.increment(-selectedDocs.size()));
        batchCount++;
        
        if (batchCount > 0) {
            batchTasks.add(batch.commit());
        }
        
        if (!batchTasks.isEmpty()) {
            Tasks.whenAll(batchTasks)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Successfully moved " + selectedDocs.size() + " entrants to SelectedEntrants");
                        markAsProcessed(eventRef, new SelectionCallback() {
                            @Override
                            public void onComplete(int selectedCount) {
                                if (callback != null) {
                                    callback.onComplete(selectedDocs.size());
                                }
                            }
                            
                            @Override
                            public void onError(String error) {
                                if (callback != null) {
                                    callback.onComplete(selectedDocs.size());
                                }
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to move entrants to SelectedEntrants", e);
                        if (callback != null) {
                            callback.onError("Failed to move entrants: " + e.getMessage());
                        }
                    });
        } else {
            markAsProcessed(eventRef, callback);
        }
    }
    
    private void markAsProcessed(DocumentReference eventRef, SelectionCallback callback) {
        eventRef.update("selectionProcessed", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Marked event as selection processed");
                    if (callback != null) {
                        callback.onComplete(0);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to mark event as processed", e);
                    if (callback != null) {
                        callback.onComplete(0);
                    }
                });
    }
}
