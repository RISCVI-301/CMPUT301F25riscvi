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
                    
                    // Move user to AdmittedEntrants so event appears in "upcoming events" immediately
                    if (admittedRepo != null) {
                        Log.d(TAG, "Calling admittedRepo.admit() for eventId: " + eventId + ", uid: " + uid);
                        return admittedRepo.admit(eventId, uid)
                                .continueWithTask(admitTask -> {
                                    if (admitTask.isSuccessful()) {
                                        Log.d(TAG, "✅ User " + uid + " accepted invitation and moved to AdmittedEntrants - event will appear in upcoming events");
                                        // Check if we should send not-selected notifications after this acceptance
                                        checkAndSendNotSelectedNotifications(eventId);
                                    } else {
                                        Log.e(TAG, "Failed to admit user to event", admitTask.getException());
                                        // Still notify and continue even if admit fails
                                    }
                                    notifyUid(uid);
                                    return Tasks.forResult(null);
                                });
                    } else {
                        Log.e(TAG, "admittedRepo is NULL! Cannot admit user to event.");
                        notifyUid(uid);
                        return Tasks.forResult(null);
                    }
                });
    }

    @Override
    public Task<Void> decline(String invitationId, String eventId, String uid) {
        Log.d(TAG, "Decline called with invitationId: " + invitationId + ", eventId: " + eventId + ", uid: " + uid);
        
            DocumentReference eventRef = db.collection("events").document(eventId);
            DocumentReference waitlistDoc = eventRef.collection("WaitlistedEntrants").document(uid);
            DocumentReference selectedDoc = eventRef.collection("SelectedEntrants").document(uid);
            DocumentReference nonSelectedDoc = eventRef.collection("NonSelectedEntrants").document(uid);
            DocumentReference cancelledDoc = eventRef.collection("CancelledEntrants").document(uid);
            
            Log.d(TAG, "Fetching user document...");
            return db.collection("users").document(uid).get().continueWithTask(userTask -> {
                if (!userTask.isSuccessful()) {
                    Log.e(TAG, "Failed to fetch user document", userTask.getException());
                }
                
                DocumentSnapshot userDoc = userTask.isSuccessful() ? userTask.getResult() : null;
                Map<String, Object> cancelledData = buildCancelledEntry(uid, userDoc);
            
            // Delete invitation document and notificationRequests entry
            return deleteInvitationAndNotificationRequests(invitationId, eventId, uid)
                    .continueWithTask(deleteTask -> {
                        if (!deleteTask.isSuccessful()) {
                            Log.e(TAG, "Failed to delete invitation and notification requests", deleteTask.getException());
                            return Tasks.forException(deleteTask.getException());
                        }
                
                Log.d(TAG, "Building batch operations:");
                Log.d(TAG, "  1. Update invitation status to DECLINED");
                Log.d(TAG, "  2. Add to CancelledEntrants");
                Log.d(TAG, "  3. Delete from WaitlistedEntrants");
                Log.d(TAG, "  4. Delete from SelectedEntrants");
                Log.d(TAG, "  5. Delete from NonSelectedEntrants");
                
                WriteBatch batch = db.batch();
                batch.set(cancelledDoc, cancelledData, SetOptions.merge());
                // CRITICAL: Remove from ALL other collections to ensure mutual exclusivity
                batch.delete(waitlistDoc);
                batch.delete(selectedDoc);
                batch.delete(nonSelectedDoc);
            
            Log.d(TAG, "Committing batch...");
            return batch.commit()
                    .continueWith(commitTask -> {
                        if (commitTask.isSuccessful()) {
                            Log.d(TAG, "✅ SUCCESS: Batch committed successfully!");
                            Log.d(TAG, "  ✓ Invitation status → DECLINED");
                            Log.d(TAG, "  ✓ User added to → CancelledEntrants");
                            Log.d(TAG, "  ✓ User deleted from → WaitlistedEntrants");
                            Log.d(TAG, "  ✓ User deleted from → SelectedEntrants");
                            Log.d(TAG, "  ✓ User deleted from → NonSelectedEntrants");
                            
                            Invitation inv = byId.get(invitationId);
                            if (inv != null) {
                                inv.setStatus(Status.DECLINED);
                            }
                            notifyUid(uid);
                            
                            // NOTE: Automatic replacement is disabled - organizer must manually replace via button
                        } else {
                            Log.e(TAG, "❌ FAILED: Batch commit failed!");
                            Log.e(TAG, "Error: " + commitTask.getException().getMessage(), commitTask.getException());
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
    
    /**
     * Checks if event capacity is full and all selected entrants have accepted,
     * then sends not-selected notifications to remaining non-selected entrants.
     */
    private void checkAndSendNotSelectedNotifications(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "Checking if should send not-selected notifications for event: " + eventId);
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        eventRef.get().addOnSuccessListener(eventDoc -> {
            if (eventDoc == null || !eventDoc.exists()) {
                Log.w(TAG, "Event not found when checking for not-selected notifications");
                return;
            }
            
            // Check if sorry notification already sent
            Boolean sorryNotificationSent = eventDoc.getBoolean("sorryNotificationSent");
            if (Boolean.TRUE.equals(sorryNotificationSent)) {
                Log.d(TAG, "Sorry notification already sent for event " + eventId);
                return;
            }
            
            // Get event capacity
            Object capacityObj = eventDoc.get("capacity");
            int capacity = capacityObj != null ? ((Number) capacityObj).intValue() : -1;
            
            if (capacity <= 0) {
                Log.d(TAG, "Event has no capacity limit, skipping not-selected notification check");
                return;
            }
            
            // Check AdmittedEntrants count (these are users who accepted)
            eventRef.collection("AdmittedEntrants").get()
                    .addOnSuccessListener(admittedSnapshot -> {
                        int admittedCount = admittedSnapshot != null ? admittedSnapshot.size() : 0;
                        
                        Log.d(TAG, "Event capacity: " + capacity + ", Admitted count: " + admittedCount);
                        
                        // Check if capacity is full
                        if (admittedCount >= capacity) {
                            Log.d(TAG, "✅ Capacity is full! Checking if all selected have accepted...");
                            
                            // Check SelectedEntrants - if empty or all have accepted invitations, send notifications
                            eventRef.collection("SelectedEntrants").get()
                                    .addOnSuccessListener(selectedSnapshot -> {
                                        int selectedCount = selectedSnapshot != null ? selectedSnapshot.size() : 0;
                                        
                                        // Check if there are any pending invitations for this event
                                        db.collection("invitations")
                                                .whereEqualTo("eventId", eventId)
                                                .whereEqualTo("status", "PENDING")
                                                .get()
                                                .addOnSuccessListener(invitationSnapshot -> {
                                                    int pendingInvitations = invitationSnapshot != null ? invitationSnapshot.size() : 0;
                                                    
                                                    Log.d(TAG, "Selected count: " + selectedCount + ", Pending invitations: " + pendingInvitations);
                                                    
                                                    // If no pending invitations, all selected have responded
                                                    // Send not-selected notifications
                                                    if (pendingInvitations == 0 && selectedCount == 0) {
                                                        Log.d(TAG, "✅ All selected entrants have accepted! Sending not-selected notifications...");
                                                        sendNotSelectedNotifications(eventId, eventDoc);
                                                    } else {
                                                        Log.d(TAG, "Still waiting for " + pendingInvitations + " pending invitations");
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "Failed to check pending invitations", e);
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to check SelectedEntrants", e);
                                    });
                        } else {
                            Log.d(TAG, "Capacity not full yet (" + admittedCount + "/" + capacity + ")");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to check AdmittedEntrants", e);
                    });
        })
        .addOnFailureListener(e -> {
            Log.e(TAG, "Failed to load event for not-selected notification check", e);
        });
    }
    
    /**
     * Sends not-selected notifications to all non-selected entrants.
     */
    private void sendNotSelectedNotifications(String eventId, DocumentSnapshot eventDoc) {
        String eventTitle = eventDoc.getString("title");
        if (eventTitle == null || eventTitle.isEmpty()) {
            eventTitle = "this event";
        }
        
        Log.d(TAG, "Sending not-selected notifications for event: " + eventId);
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        String finalEventTitle = eventTitle;
        eventRef.collection("NonSelectedEntrants").get()
                .addOnSuccessListener(nonSelectedSnapshot -> {
                    if (nonSelectedSnapshot == null || nonSelectedSnapshot.isEmpty()) {
                        Log.d(TAG, "No non-selected entrants to notify for event " + eventId);
                        // Mark as sent even if no entrants
                        markSorryNotificationSent(eventId);
                        return;
                    }
                    
                    List<String> userIds = new ArrayList<>();
                    for (DocumentSnapshot doc : nonSelectedSnapshot.getDocuments()) {
                        userIds.add(doc.getId());
                    }
                    
                    if (userIds.isEmpty()) {
                        Log.d(TAG, "No user IDs found in NonSelectedEntrants");
                        markSorryNotificationSent(eventId);
                        return;
                    }
                    
                    Log.d(TAG, "Sending not-selected notifications to " + userIds.size() + " users");
                    
                    // Create notification request
                    Map<String, Object> notificationRequest = new HashMap<>();
                    notificationRequest.put("eventId", eventId);
                    notificationRequest.put("eventTitle", finalEventTitle);
                    notificationRequest.put("userIds", userIds);
                    notificationRequest.put("groupType", "sorry");
                    notificationRequest.put("title", "Selection Complete: " + finalEventTitle);
                    notificationRequest.put("message", "Thank you for your interest in \"" + finalEventTitle + "\". The selection process has been completed. We appreciate your participation and hope to see you at future events!");
                    notificationRequest.put("status", "PENDING");
                    notificationRequest.put("createdAt", System.currentTimeMillis());
                    notificationRequest.put("processed", false);
                    
                    // Write to notificationRequests collection - Cloud Functions will handle sending
                    db.collection("notificationRequests").add(notificationRequest)
                            .addOnSuccessListener(docRef -> {
                                Log.d(TAG, "✓ Created not-selected notification request for " + userIds.size() + " users");
                                markSorryNotificationSent(eventId);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to create not-selected notification request", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load NonSelectedEntrants for event " + eventId, e);
                });
    }
    
    /**
     * Marks the event as having sent the sorry notification.
     */
    private void markSorryNotificationSent(String eventId) {
        db.collection("events").document(eventId)
                .update("sorryNotificationSent", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Marked event as having sent sorry notification");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to mark sorry notification as sent", e);
                });
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
