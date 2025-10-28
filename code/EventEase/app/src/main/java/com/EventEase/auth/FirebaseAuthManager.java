package com.EventEase.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Prod-name implementation backed by FirebaseAuth.
 * If no user is signed in yet, falls back to a provided dev UID so the app can run.
 */
public class FirebaseAuthManager implements AuthManager {

    private final FirebaseAuth auth;
    private final String devFallbackUid; // e.g., "demo-uid-123"

    public FirebaseAuthManager(String devFallbackUid) {
        this.auth = FirebaseAuth.getInstance();
        this.devFallbackUid = devFallbackUid;
    }

    @Override
    public String getUid() {
        FirebaseUser user = auth.getCurrentUser();
        return (user != null && user.getUid() != null) ? user.getUid() : devFallbackUid;
    }
}
