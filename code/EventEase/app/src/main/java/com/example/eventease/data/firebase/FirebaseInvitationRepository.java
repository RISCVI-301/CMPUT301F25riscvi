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
        
        Query query = db.collection("invitations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "PENDING")
                .orderBy("issuedAt", Query.Direction.DESCENDING);
        
        com.google.firebase.firestore.ListenerRegistration firestoreReg = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Error listening to invitations", e);
                return;
            }
            
            if (snapshots != null) {
                byId.clear();
                Date now = new Date();
                for (QueryDocumentSnapshot doc : snapshots) {
                    try {
                        Invitation inv = documentToInvitation(doc);
                        if (inv != null && (inv.getExpiresAt() == null || inv.getExpiresAt().after(now))) {
                            byId.put(inv.getId(), inv);
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "Error parsing invitation document", ex);
                    }
                }
                
                notifyUid(uid);
            }
        });
        
        firestoreListeners.put(uid, firestoreReg);
        
        loadInitialInvitations(uid).addOnSuccessListener(invitations -> {
            notifyUid(uid);
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
                    Log.e(TAG, "Error parsing invitation document", e);
                }
            }
            
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
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "ACCEPTED");
        updates.put("acceptedAt", System.currentTimeMillis());
        
        return db.collection("invitations").document(invitationId).update(updates)
                .continueWithTask(updateTask -> {
                    if (!updateTask.isSuccessful()) {
                        Log.e(TAG, "Failed to update invitation status", updateTask.getException());
                        return Tasks.forException(updateTask.getException());
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
            
            Map<String, Object> invitationUpdates = new HashMap<>();
            invitationUpdates.put("status", "DECLINED");
            invitationUpdates.put("declinedAt", System.currentTimeMillis());
            
            WriteBatch batch = db.batch();
            batch.update(db.collection("invitations").document(invitationId), invitationUpdates);
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
                        } else {
                            Log.e(TAG, "FAILED to decline invitation and move to cancelled", commitTask.getException());
                        }
                        return null;
                    });
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
        return byId.values().stream()
                .filter(i -> uid.equals(i.getUid()))
                .filter(i -> i.getStatus() == Status.PENDING)
                .filter(i -> i.getExpiresAt() == null || i.getExpiresAt().after(now))
                .sorted(Comparator.comparing(Invitation::getIssuedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
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
