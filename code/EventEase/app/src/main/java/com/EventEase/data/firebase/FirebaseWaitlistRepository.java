package com.EventEase.data.firebase;

import com.EventEase.data.WaitlistRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FirebaseWaitlistRepository
 * Prod name. Currently in-memory for Step 1.
 * TODO(salaar): Replace with Firebase writes/reads.
 */
public class FirebaseWaitlistRepository implements WaitlistRepository {

    private final Set<String> membership = ConcurrentHashMap.newKeySet();
    private final FirebaseEventRepository eventRepo;

    public FirebaseWaitlistRepository(FirebaseEventRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    private static String key(String eventId, String uid) {
        return eventId + "||" + uid;
    }

    @Override
    public Task<Void> join(String eventId, String uid) {
        if (!eventRepo.isKnown(eventId)) {
            return Tasks.forException(new NoSuchElementException("Unknown event " + eventId));
        }
        boolean added = membership.add(key(eventId, uid));
        if (added) {
            eventRepo.incrementWaitlist(eventId);
        }
        return Tasks.forResult(null);
    }

    @Override
    public Task<Boolean> isJoined(String eventId, String uid) {
        return Tasks.forResult(membership.contains(key(eventId, uid)));
    }
}
