package com.EventEase.auth;

/** Swap wiring here later (e.g., with DI). */
public final class AuthProvider {
    private static final String DEV_UID = "demo-uid-123";
    private static final AuthManager INSTANCE =
            new FirebaseAuthManager(DEV_UID); // or new DevAuthManager(DEV_UID)

    private AuthProvider() {}

    public static AuthManager get() { return INSTANCE; }
}
