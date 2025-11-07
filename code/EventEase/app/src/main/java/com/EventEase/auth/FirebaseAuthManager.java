package com.EventEase.auth;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public final class FirebaseAuthManager implements AuthManager {
    private final FirebaseAuth auth;

    public FirebaseAuthManager() {
        this.auth = FirebaseAuth.getInstance();
    }

    @Override
    public boolean isAuthenticated() {
        return auth.getCurrentUser() != null;
    }

    @Override
    @NonNull
    public String getUid() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) throw new IllegalStateException("No authenticated user");
        String uid = user.getUid();
        if (uid == null || uid.isEmpty()) {
            throw new IllegalStateException("Authenticated user has no UID");
        }
        return uid;
    }
}
