package com.example.eventease.data.firebase;

import android.util.Log;

import com.example.eventease.data.AdmittedRepository;
import com.example.eventease.data.InvitationListener;
import com.example.eventease.data.InvitationRepository;
import com.example.eventease.data.ListenerRegistration;
import com.example.eventease.model.Invitation;
import com.example.eventease.model.Invitation.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Firebase implementation of InvitationRepository.
 * Handles invitation management with Firebase backend and provides real-time updates.
 */
public class FirebaseInvitationRepository implements InvitationRepository {

    private static final String TAG = "InvitationRepository";
    private final Map<String, Invitation> byId = new ConcurrentHashMap<>();
    private final Map<String, List<InvitationListener>> listenersByUid = new ConcurrentHashMap<>();
    private final FirebaseFirestore db;
    private AdmittedRepository admittedRepo;
    private final Map<String, com.google.firebase.firestore.ListenerRegistration> firestoreListeners = new ConcurrentHashMap<>();

    public FirebaseInvitationRepository(List<Invitation> seed) {
        this.db = FirebaseFirestore.getInstance();
        for (Invitation inv : seed) {
            byId.put(inv.getId(), inv);
        }
    }
    
    public void setAdmittedRepository(AdmittedRepository admittedRepo) {
        this.admittedRepo = admittedRepo;
    }

    @Override
    public ListenerRegistration listenActive(String uid, InvitationListener l) {
        listenersByUid.computeIfAbsent(uid, k -> new ArrayList<>()).add(l);
        
        // Use simpler query without orderBy to avoid index requirement
        // We'll filter and sort in memory
        Query query = db.collection("invitations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "PENDING");
        
        com.google.firebase.firestore.ListenerRegistration firestoreReg = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Error listening to invitations for uid: " + uid, e);
                // If it's an index error, log it clearly
                if (e instanceof FirebaseFirestoreException) {
                    FirebaseFirestoreException firestoreEx = (FirebaseFirestoreException) e;
                    Log.e(TAG, "Firestore error code: " + firestoreEx.getCode() + ", message: " + e.getMessage());
                }
                return;
            }
            
            if (snapshots != null) {
                byId.clear();
                Date now = new Date();
                int validCount = 0;
                int totalCount = 0;
                for (QueryDocumentSnapshot doc : snapshots) {
                    totalCount++;
                    try {
                        Invitation inv = documentToInvitation(doc);
                        if (inv != null && inv.getStatus() == Status.PENDING) {
                            // Check expiration
                            if (inv.getExpiresAt() == null || inv.getExpiresAt().after(now)) {
                                byId.put(inv.getId(), inv);
                                validCount++;
                            } else {
                                Log.d(TAG, "Invitation expired: " + inv.getId());
                            }
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "Error parsing invitation document: " + doc.getId(), ex);
                    }
                }
                Log.d(TAG, "Listener: Found " + totalCount + " documents, " + validCount + " valid active invitations for uid: " + uid);
                notifyUid(uid);
            }
        });
        
        firestoreListeners.put(uid, firestoreReg);
        
        // Load initial invitations (using simpler query that doesn't require index)
        loadInitialInvitationsWithoutOrderBy(uid).addOnSuccessListener(invitations -> {
            Log.d(TAG, "Initial invitations loaded: " + invitations.size() + " for uid: " + uid);
            notifyUid(uid);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to load initial invitations", e);
        });
        
        return new ListenerRegistration() {
            private boolean removed = false;
            @Override public void remove() {
                if (removed) return;
                List<InvitationListener> ls = listenersByUid.get(uid);
                if (ls != null) ls.remove(l);
                
                com.google.firebase.firestore.ListenerRegistration firestoreReg = firestoreListeners.remove(uid);
                if (firestoreReg != null) {
                    firestoreReg.remove();
                }
                
                removed = true;
            }
        };
    }
    
    private Task<List<Invitation>> loadInitialInvitations(String uid) {
        Query query = db.collection("invitations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "PENDING")
                .orderBy("issuedAt", Query.Direction.DESCENDING);
        
        return query.get().continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Exception exception = task.getException();
                if (exception instanceof FirebaseFirestoreException) {
                    FirebaseFirestoreException firestoreEx = (FirebaseFirestoreException) exception;
                    if (firestoreEx.getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        Log.w(TAG, "Index required for ordered query, falling back to unordered query");
                        // Fallback to query without orderBy - this will be handled asynchronously
                        // Return empty list for now, the fallback will update via notifyUid
                        return new ArrayList<>();
                    }
                }
                Log.e(TAG, "Failed to load initial invitations", exception);
                return new ArrayList<>();
            }
            
            List<Invitation> invitations = new ArrayList<>();
            Date now = new Date();
            for (QueryDocumentSnapshot doc : task.getResult()) {
                try {
                    Invitation inv = documentToInvitation(doc);
                    if (inv != null && (inv.getExpiresAt() == null || inv.getExpiresAt().after(now))) {
                        invitations.add(inv);
                        byId.put(inv.getId(), inv);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing invitation document: " + doc.getId(), e);
                }
            }
            
            Log.d(TAG, "Loaded " + invitations.size() + " initial invitations for uid: " + uid);
            return invitations;
        });
    }
    
    private Task<List<Invitation>> loadInitialInvitationsWithoutOrderBy(String uid) {
        // Fallback query without orderBy (doesn't require index)
        Query query = db.collection("invitations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "PENDING");
        
        return query.get().continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Log.e(TAG, "Failed to load invitations (fallback query)", task.getException());
                return new ArrayList<>();
            }
            
            List<Invitation> invitations = new ArrayList<>();
            Date now = new Date();
            for (QueryDocumentSnapshot doc : task.getResult()) {
                try {
                    Invitation inv = documentToInvitation(doc);
                    if (inv != null && (inv.getExpiresAt() == null || inv.getExpiresAt().after(now))) {
                        invitations.add(inv);
                        byId.put(inv.getId(), inv);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing invitation document: " + doc.getId(), e);
                }
            }
            
            // Sort manually by issuedAt descending
            invitations.sort((a, b) -> {
                Date aDate = a.getIssuedAt();
                Date bDate = b.getIssuedAt();
                if (aDate == null && bDate == null) return 0;
                if (aDate == null) return 1;
                if (bDate == null) return -1;
                return bDate.compareTo(aDate); // Descending
            });
            
            Log.d(TAG, "Loaded " + invitations.size() + " invitations (fallback, no index) for uid: " + uid);
            return invitations;
        });
    }
    
    private Invitation documentToInvitation(DocumentSnapshot doc) {
        if (!doc.exists()) {
            return null;
        }
        
        Invitation inv = new Invitation();
        inv.setId(doc.getId());
        inv.setEventId(doc.getString("eventId"));
        inv.setUid(doc.getString("uid"));
        
        String statusStr = doc.getString("status");
        if ("PENDING".equals(statusStr)) {
            inv.setStatus(Status.PENDING);
        } else if ("ACCEPTED".equals(statusStr)) {
            inv.setStatus(Status.ACCEPTED);
        } else if ("DECLINED".equals(statusStr)) {
            inv.setStatus(Status.DECLINED);
        }
        
        Long issuedAt = doc.getLong("issuedAt");
        if (issuedAt != null) {
            inv.setIssuedAt(new Date(issuedAt));
        }
        
        Long expiresAt = doc.getLong("expiresAt");
        if (expiresAt != null) {
            inv.setExpiresAt(new Date(expiresAt));
        }
        
        return inv;
    }

    @Override
    public Task<Void> accept(String invitationId, String eventId, String uid) {
        Log.d(TAG, "Accept called with invitationId: " + invitationId + ", eventId: " + eventId + ", uid: " + uid);
        
        // Delete invitation document and notificationRequests entry
        return deleteInvitationAndNotificationRequests(invitationId, eventId, uid)
                .continueWithTask(deleteTask -> {
                    if (!deleteTask.isSuccessful()) {
                        Log.e(TAG, "Failed to delete invitation and notification requests", deleteTask.getException());
                        return Tasks.forException(deleteTask.getException());
                    }
                    
                    Invitation inv = byId.get(invitationId);
                    if (inv != null) {
                        inv.setStatus(Status.ACCEPTED);
                    }
                    
                    if (admittedRepo != null) {
                        Log.d(TAG, "Calling admittedRepo.admit() for eventId: " + eventId + ", uid: " + uid);
                        return admittedRepo.admit(eventId, uid);
                    } else {
                        Log.e(TAG, "admittedRepo is NULL! Cannot admit user to event.");
                        return Tasks.forResult(null);
                    }
                })
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Successfully completed admit task");
                        notifyUid(uid);
                    } else {
                        Log.e(TAG, "Admit task failed", task.getException());
                    }
                    return task.isSuccessful() ? Tasks.forResult(null) : Tasks.forException(task.getException());
                });
    }

    @Override
    public Task<Void> decline(String invitationId, String eventId, String uid) {
        Log.d(TAG, "Decline called with invitationId: " + invitationId + ", eventId: " + eventId + ", uid: " + uid);
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference waitlistDoc = eventRef.collection("WaitlistedEntrants").document(uid);
        DocumentReference selectedDoc = eventRef.collection("SelectedEntrants").document(uid);
        DocumentReference cancelledDoc = eventRef.collection("CancelledEntrants").document(uid);
        
        return db.collection("users").document(uid).get().continueWithTask(userTask -> {
            DocumentSnapshot userDoc = userTask.isSuccessful() ? userTask.getResult() : null;
            Map<String, Object> cancelledData = buildCancelledEntry(uid, userDoc);
            
            // Delete invitation document and notificationRequests entry
            return deleteInvitationAndNotificationRequests(invitationId, eventId, uid)
                    .continueWithTask(deleteTask -> {
                        if (!deleteTask.isSuccessful()) {
                            Log.e(TAG, "Failed to delete invitation and notification requests", deleteTask.getException());
                            return Tasks.forException(deleteTask.getException());
                        }
                        
                        WriteBatch batch = db.batch();
                        batch.set(cancelledDoc, cancelledData, SetOptions.merge());
                        batch.delete(waitlistDoc);
                        batch.delete(selectedDoc);
                        
                        return batch.commit()
                                .continueWith(commitTask -> {
                                    if (commitTask.isSuccessful()) {
                                        Log.d(TAG, "SUCCESS: Invitation declined, user moved to CancelledEntrants");
                                        Invitation inv = byId.get(invitationId);
                                        if (inv != null) {
                                            inv.setStatus(Status.DECLINED);
                                        }
                                        notifyUid(uid);
                                        
                                        // NOTE: Automatic replacement is disabled - organizer must manually replace via button
                                    } else {
                                        Log.e(TAG, "FAILED to decline invitation and move to cancelled", commitTask.getException());
                                    }
                                    return null;
                                });
                    });
        });
    }
    
    /**
     * Deletes the invitation document and any related notificationRequests entries.
     */
    private Task<Void> deleteInvitationAndNotificationRequests(String invitationId, String eventId, String uid) {
        // Delete invitation document
        Task<Void> deleteInvitationTask = db.collection("invitations").document(invitationId).delete();
        
        // Delete notificationRequests entries for this eventId and userId
        Task<QuerySnapshot> findNotificationRequestsTask = db.collection("notificationRequests")
                .whereEqualTo("eventId", eventId)
                .whereArrayContains("userIds", uid)
                .get();
        
        return Tasks.whenAllComplete(deleteInvitationTask, findNotificationRequestsTask)
                .continueWithTask(allTasks -> {
                    // Delete invitation is done
                    if (!deleteInvitationTask.isSuccessful()) {
                        Log.e(TAG, "Failed to delete invitation document", deleteInvitationTask.getException());
                    } else {
                        Log.d(TAG, "Successfully deleted invitation document: " + invitationId);
                    }
                    
                    // Delete notificationRequests
                    if (findNotificationRequestsTask.isSuccessful() && findNotificationRequestsTask.getResult() != null) {
                        QuerySnapshot snapshot = findNotificationRequestsTask.getResult();
                        WriteBatch batch = db.batch();
                        int deleteCount = 0;
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            batch.delete(doc.getReference());
                            deleteCount++;
                        }
                        if (deleteCount > 0) {
                            Log.d(TAG, "Deleting " + deleteCount + " notificationRequests entries for eventId: " + eventId + ", uid: " + uid);
                            return batch.commit().continueWith(commitTask -> {
                                if (commitTask.isSuccessful()) {
                                    Log.d(TAG, "Successfully deleted notificationRequests entries");
                                } else {
                                    Log.e(TAG, "Failed to delete notificationRequests entries", commitTask.getException());
                                }
                                return null;
                            });
                        } else {
                            Log.d(TAG, "No notificationRequests entries found to delete");
                        }
                    } else {
                        Log.w(TAG, "Failed to query notificationRequests or no results found");
                    }
                    
                    return Tasks.forResult(null);
                });
    }
    
    private Map<String, Object> buildCancelledEntry(String uid, DocumentSnapshot userDoc) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("cancelledAt", System.currentTimeMillis());

        if (userDoc != null && userDoc.exists()) {
            String displayName = userDoc.getString("fullName");
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = userDoc.getString("name");
            }
            if (displayName == null || displayName.trim().isEmpty()) {
                String first = userDoc.getString("firstName");
                String last = userDoc.getString("lastName");
                displayName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
            }

            putIfString(data, "displayName", displayName);
            putIfString(data, "fullName", userDoc.getString("fullName"));
            putIfString(data, "name", userDoc.getString("name"));
            putIfString(data, "firstName", userDoc.getString("firstName"));
            putIfString(data, "lastName", userDoc.getString("lastName"));
            putIfString(data, "email", userDoc.getString("email"));
            putIfString(data, "phoneNumber", userDoc.getString("phoneNumber"));
            putIfString(data, "photoUrl", userDoc.getString("photoUrl"));
        }

        return data;
    }
    
    private void putIfString(Map<String, Object> target, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.put(key, value);
        }
    }

    private List<Invitation> activeFor(String uid) {
        Date now = new Date();
        List<Invitation> active = byId.values().stream()
                .filter(i -> uid.equals(i.getUid()))
                .filter(i -> i.getStatus() == Status.PENDING)
                .filter(i -> i.getExpiresAt() == null || i.getExpiresAt().after(now))
                .sorted(Comparator.comparing(Invitation::getIssuedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        Log.d(TAG, "activeFor(" + uid + "): returning " + active.size() + " active invitations");
        return active;
    }

    private void notifyUid(String uid) {
        List<InvitationListener> ls = listenersByUid.get(uid);
        if (ls != null) {
            List<Invitation> current = activeFor(uid);
            for (InvitationListener l : new ArrayList<>(ls)) {
                l.onChanged(current);
            }
        }
    }
}
