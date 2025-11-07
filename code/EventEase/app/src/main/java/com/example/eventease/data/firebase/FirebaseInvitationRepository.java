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
import com.google.firebase.firestore.FirebaseFirestore;

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
        l.onChanged(activeFor(uid));
        return new ListenerRegistration() {
            private boolean removed = false;
            @Override public void remove() {
                if (removed) return;
                List<InvitationListener> ls = listenersByUid.get(uid);
                if (ls != null) ls.remove(l);
                removed = true;
            }
        };
    }

    @Override
    public Task<Void> accept(String invitationId, String eventId, String uid) {
        Log.d(TAG, "Accept called with invitationId: " + invitationId + ", eventId: " + eventId + ", uid: " + uid);
        
        Invitation inv = byId.get(invitationId);
        if (inv == null) {
            Log.e(TAG, "Invitation not found for ID: " + invitationId);
            return Tasks.forException(new NoSuchElementException("Invitation not found"));
        }
        
        Log.d(TAG, "Setting invitation status to ACCEPTED for invitation: " + invitationId);
        inv.setStatus(Status.ACCEPTED);
        
        // Update in Firebase
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "ACCEPTED");
        updates.put("acceptedAt", System.currentTimeMillis());
        
        // Also admit the user to the event
        Task<Void> admitTask;
        if (admittedRepo != null) {
            Log.d(TAG, "Calling admittedRepo.admit() for eventId: " + eventId + ", uid: " + uid);
            admitTask = admittedRepo.admit(eventId, uid);
        } else {
            Log.e(TAG, "admittedRepo is NULL! Cannot admit user to event.");
            admitTask = Tasks.forResult(null);
        }
        
        return admitTask.continueWithTask(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Successfully completed admit task");
            } else {
                Log.e(TAG, "Admit task failed", task.getException());
            }
            notifyUid(uid);
            Log.d(TAG, "User " + uid + " accepted invitation for event " + eventId);
            return Tasks.forResult(null);
        });
    }

    @Override
    public Task<Void> decline(String invitationId, String eventId, String uid) {
        Log.d(TAG, "Decline called with invitationId: " + invitationId + ", eventId: " + eventId + ", uid: " + uid);
        
        Invitation inv = byId.get(invitationId);
        if (inv == null) {
            Log.e(TAG, "Invitation not found for ID: " + invitationId);
            return Tasks.forException(new NoSuchElementException("Invitation not found"));
        }
        
        Log.d(TAG, "Setting invitation status to DECLINED for invitation: " + invitationId);
        inv.setStatus(Status.DECLINED);
        
        // Update in Firebase
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "DECLINED");
        updates.put("declinedAt", System.currentTimeMillis());
        
        return db.collection("invitations")
                .document(invitationId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "SUCCESS: Invitation " + invitationId + " declined in Firebase");
                    notifyUid(uid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "FAILED to decline invitation " + invitationId, e);
                })
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Task completed successfully for decline operation");
                    } else {
                        Log.e(TAG, "Task failed for decline operation", task.getException());
                    }
                    return null;
                });
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
