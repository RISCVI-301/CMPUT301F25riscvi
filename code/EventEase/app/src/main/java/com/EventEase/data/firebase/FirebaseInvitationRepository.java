package com.EventEase.data.firebase;

import com.EventEase.data.InvitationListener;
import com.EventEase.data.InvitationRepository;
import com.EventEase.data.ListenerRegistration;
import com.EventEase.model.Invitation;
import com.EventEase.model.Invitation.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * FirebaseInvitationRepository
 * Prod name. In-memory for Step 1.
 * TODO(salaar): Replace with Firebase collection + queries + snapshots.
 */
public class FirebaseInvitationRepository implements InvitationRepository {

    private final Map<String, Invitation> byId = new ConcurrentHashMap<>();
    private final Map<String, List<InvitationListener>> listenersByUid = new ConcurrentHashMap<>();

    public FirebaseInvitationRepository(List<Invitation> seed) {
        for (Invitation inv : seed) {
            byId.put(inv.getId(), inv);
        }
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
        Invitation inv = byId.get(invitationId);
        if (inv == null) return Tasks.forException(new NoSuchElementException("Invitation not found"));
        inv.setStatus(Status.ACCEPTED);
        notifyUid(uid);
        return Tasks.forResult(null);
    }

    @Override
    public Task<Void> decline(String invitationId, String eventId, String uid) {
        Invitation inv = byId.get(invitationId);
        if (inv == null) return Tasks.forException(new NoSuchElementException("Invitation not found"));
        inv.setStatus(Status.DECLINED);
        notifyUid(uid);
        return Tasks.forResult(null);
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
