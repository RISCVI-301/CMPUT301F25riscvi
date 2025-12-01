package com.example.eventease.ui.entrant.profile;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;

public class ProfileDeletionHelper {
    
    private static final String TAG = "ProfileDeletionHelper";
    private final FirebaseFirestore db;
    private final Context context;
    
    public interface DeletionCallback {
        void onDeletionComplete();
        void onDeletionFailure(String error);
    }
    
    public ProfileDeletionHelper(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
    }
    
    /**
     * Deletes all events created by an organizer.
     * @param uid the organizer's user ID
     * @return a Task that completes when all events are deleted
     */
    public com.google.android.gms.tasks.Task<Void> deleteOrganizerEvents(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            Log.w(TAG, "deleteOrganizerEvents: UID is null or empty, skipping delete");
            return com.google.android.gms.tasks.Tasks.forResult(null);
        }

        return db.collection("events")
                .whereEqualTo("organizerId", uid)
                .get()
                .continueWithTask(queryTask -> {
                    if (!queryTask.isSuccessful() || queryTask.getResult() == null) {
                        Log.e(TAG, "deleteOrganizerEvents: Failed to fetch events for organizer: " + uid, queryTask.getException());
                        return com.google.android.gms.tasks.Tasks.forResult(null);
                    }

                    QuerySnapshot qs = queryTask.getResult();
                    if (qs.isEmpty()) {
                        Log.d(TAG, "deleteOrganizerEvents: No events found for organizer: " + uid);
                        return com.google.android.gms.tasks.Tasks.forResult(null);
                    }

                    Log.d(TAG, "deleteOrganizerEvents: Found " + qs.size() + " events to delete for organizer: " + uid);
                    List<com.google.android.gms.tasks.Task<Void>> deleteTasks = new ArrayList<>();
                    
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        final String eventId = d.getId();
                        deleteTasks.add(d.getReference().delete()
                                .addOnSuccessListener(aVoid ->
                                        Log.d(TAG, "deleteOrganizerEvents: Deleted event " + eventId +
                                                " for organizer: " + uid))
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "deleteOrganizerEvents: Failed to delete event " +
                                                eventId + " for organizer: " + uid, e))
                                .continueWith(task -> null));
                    }

                    if (deleteTasks.isEmpty()) {
                        return com.google.android.gms.tasks.Tasks.forResult(null);
                    }

                    return com.google.android.gms.tasks.Tasks.whenAll(deleteTasks)
                            .continueWith(allTasks -> {
                                if (allTasks.isSuccessful()) {
                                    Log.d(TAG, "deleteOrganizerEvents: Successfully deleted all " + qs.size() + " events for organizer: " + uid);
                                } else {
                                    Log.e(TAG, "deleteOrganizerEvents: Some events failed to delete for organizer: " + uid, allTasks.getException());
                                }
                                return null;
                            });
                });
    }

    public void deleteAllUserReferences(String uid, DeletionCallback callback) {
        Log.d(TAG, "Starting deletion of all references for user: " + uid);
        
        db.collection("events").get()
            .addOnSuccessListener(eventsSnapshot -> {
                if (eventsSnapshot == null || eventsSnapshot.isEmpty()) {
                    Log.d(TAG, "No events found, proceeding to delete invitations");
                    deleteInvitations(uid, callback);
                    return;
                }
                
                Log.d(TAG, "Found " + eventsSnapshot.size() + " events to check");
                
                List<DocumentReference> documentsToDelete = new ArrayList<>();
                List<DocumentReference> waitlistArrayEventsToUpdate = new ArrayList<>();
                List<DocumentReference> admittedArrayEventsToUpdate = new ArrayList<>();
                
                String[] subcollections = {
                    "WaitlistedEntrants",
                    "SelectedEntrants",
                    "NonSelectedEntrants",
                    "CancelledEntrants",
                    "AdmittedEntrants"
                };
                
                for (DocumentSnapshot eventDoc : eventsSnapshot.getDocuments()) {
                    String eventId = eventDoc.getId();
                    DocumentReference eventRef = db.collection("events").document(eventId);
                    
                    for (String subcollectionName : subcollections) {
                        DocumentReference entrantRef = eventRef
                            .collection(subcollectionName)
                            .document(uid);
                        documentsToDelete.add(entrantRef);
                    }
                    
                    Object waitlist = eventDoc.get("waitlist");
                    if (waitlist instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> waitlistArray = (List<String>) waitlist;
                        if (waitlistArray.contains(uid)) {
                            waitlistArrayEventsToUpdate.add(eventRef);
                        }
                    }
                    
                    Object admitted = eventDoc.get("admitted");
                    if (admitted instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> admittedArray = (List<String>) admitted;
                        if (admittedArray.contains(uid)) {
                            admittedArrayEventsToUpdate.add(eventRef);
                        }
                    }
                }
                
                Log.d(TAG, "Found " + documentsToDelete.size() + " documents to check and delete");
                Log.d(TAG, "Found " + waitlistArrayEventsToUpdate.size() + " waitlist arrays to update");
                Log.d(TAG, "Found " + admittedArrayEventsToUpdate.size() + " admitted arrays to update");
                
                checkAndDeleteDocuments(documentsToDelete, waitlistArrayEventsToUpdate, admittedArrayEventsToUpdate, uid, callback);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to query events for deletion", e);
                deleteInvitations(uid, callback);
            });
    }
    
    private void checkAndDeleteDocuments(List<DocumentReference> documentsToDelete,
                                        List<DocumentReference> waitlistArrayEventsToUpdate,
                                        List<DocumentReference> admittedArrayEventsToUpdate,
                                        String uid,
                                        DeletionCallback callback) {
        if (documentsToDelete.isEmpty() && waitlistArrayEventsToUpdate.isEmpty() && admittedArrayEventsToUpdate.isEmpty()) {
            Log.d(TAG, "No documents to delete, proceeding to delete invitations");
            deleteInvitations(uid, callback);
            return;
        }
        
        final int[] checkedCount = {0};
        final int totalCount = documentsToDelete.size();
        final List<DocumentReference> existingDocs = new ArrayList<>();
        final List<DocumentReference> existingWaitlistEvents = new ArrayList<>();
        
        for (DocumentReference docRef : documentsToDelete) {
            docRef.get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        existingDocs.add(docRef);
                        String path = docRef.getPath();
                        if (path.contains("WaitlistedEntrants")) {
                            String eventId = docRef.getParent().getParent().getId();
                            DocumentReference eventRef = db.collection("events").document(eventId);
                            if (!existingWaitlistEvents.contains(eventRef)) {
                                existingWaitlistEvents.add(eventRef);
                            }
                        }
                        Log.d(TAG, "Found existing document: " + path);
                    }
                    checkedCount[0]++;
                    
                    if (checkedCount[0] >= totalCount) {
                        Log.d(TAG, "Checked all documents. Found " + existingDocs.size() + " existing documents to delete");
                        performDeletions(existingDocs, existingWaitlistEvents, waitlistArrayEventsToUpdate, admittedArrayEventsToUpdate, uid, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to check document: " + docRef.getPath(), e);
                    checkedCount[0]++;
                    if (checkedCount[0] >= totalCount) {
                        Log.d(TAG, "Checked all documents. Found " + existingDocs.size() + " existing documents to delete");
                        performDeletions(existingDocs, existingWaitlistEvents, waitlistArrayEventsToUpdate, admittedArrayEventsToUpdate, uid, callback);
                    }
                });
        }
        
        if (totalCount == 0) {
            performDeletions(existingDocs, existingWaitlistEvents, waitlistArrayEventsToUpdate, admittedArrayEventsToUpdate, uid, callback);
        }
    }
    
    private void performDeletions(List<DocumentReference> documentsToDelete,
                                 List<DocumentReference> waitlistEventsToUpdate,
                                 List<DocumentReference> waitlistArrayEventsToUpdate,
                                 List<DocumentReference> admittedArrayEventsToUpdate,
                                 String uid,
                                 DeletionCallback callback) {
        List<Task<Void>> deletionTasks = new ArrayList<>();
        
        final int MAX_BATCH_SIZE = 500;
        WriteBatch batch = db.batch();
        int batchCount = 0;
        
        for (DocumentReference docRef : documentsToDelete) {
            batch.delete(docRef);
            batchCount++;
            
            if (batchCount >= MAX_BATCH_SIZE) {
                final WriteBatch currentBatch = batch;
                deletionTasks.add(currentBatch.commit());
                batch = db.batch();
                batchCount = 0;
            }
        }
        
        for (DocumentReference eventRef : waitlistEventsToUpdate) {
            batch.update(eventRef, "waitlistCount", FieldValue.increment(-1));
            batchCount++;
            
            if (batchCount >= MAX_BATCH_SIZE) {
                final WriteBatch currentBatch = batch;
                deletionTasks.add(currentBatch.commit());
                batch = db.batch();
                batchCount = 0;
            }
        }
        
        if (batchCount > 0) {
            deletionTasks.add(batch.commit());
        }
        
        for (DocumentReference eventRef : waitlistArrayEventsToUpdate) {
            deletionTasks.add(eventRef.update("waitlist", FieldValue.arrayRemove(uid)));
        }
        
        for (DocumentReference eventRef : admittedArrayEventsToUpdate) {
            deletionTasks.add(eventRef.update("admitted", FieldValue.arrayRemove(uid)));
        }
        
        Log.d(TAG, "Executing " + deletionTasks.size() + " deletion tasks");
        
        if (!deletionTasks.isEmpty()) {
            Tasks.whenAll(deletionTasks)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "All deletion tasks completed successfully");
                    deleteInvitations(uid, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Some deletion tasks failed", e);
                    deleteInvitations(uid, callback);
                });
        } else {
            Log.d(TAG, "No deletion tasks to execute, proceeding to delete invitations");
            deleteInvitations(uid, callback);
        }
    }
    
    private void deleteInvitations(String uid, DeletionCallback callback) {
        db.collection("invitations")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (querySnapshot == null || querySnapshot.isEmpty()) {
                    if (callback != null) callback.onDeletionComplete();
                    return;
                }
                
                List<Task<Void>> deleteTasks = new ArrayList<>();
                for (QueryDocumentSnapshot doc : querySnapshot) {
                    deleteTasks.add(doc.getReference().delete());
                }
                
                if (!deleteTasks.isEmpty()) {
                    Tasks.whenAll(deleteTasks)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Successfully deleted invitations");
                            if (callback != null) callback.onDeletionComplete();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to delete some invitations", e);
                            if (callback != null) callback.onDeletionComplete();
                        });
                } else {
                    if (callback != null) callback.onDeletionComplete();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to query invitations for deletion", e);
                if (callback != null) callback.onDeletionComplete();
            });
    }
}

