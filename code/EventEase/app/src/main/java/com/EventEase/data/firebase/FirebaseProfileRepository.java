package com.EventEase.data.firebase;

import com.EventEase.data.ProfileRepository;
import com.EventEase.model.Profile;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Firebase implementation of ProfileRepository.
 * Manages user profile data in Firestore.
 */
public class FirebaseProfileRepository implements ProfileRepository {

    private final Map<String, Profile> byUid = new ConcurrentHashMap<>();

    public FirebaseProfileRepository(Profile... seed) {
        for (Profile p : seed) {
            if (p != null && p.getUid() != null) byUid.put(p.getUid(), p);
        }
    }

    @Override
    public Task<Profile> get(String uid) {
        Profile p = byUid.get(uid);
        if (p != null) return Tasks.forResult(p);
        return Tasks.forException(new NoSuchElementException("Profile not found: " + uid));
    }

    @Override
    public Task<Void> upsert(String uid, Profile p) {
        if (p == null) return Tasks.forException(new IllegalArgumentException("Profile is null"));
        p.setUid(uid);
        byUid.put(uid, p);
        return Tasks.forResult(null);
    }
}
