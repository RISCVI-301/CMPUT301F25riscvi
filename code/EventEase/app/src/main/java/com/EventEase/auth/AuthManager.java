package com.EventEase.auth;

/** Stable auth contract for the app. */
public interface AuthManager {
    /** Returns a UID (Firebase UID if signed in; otherwise a dev fallback). */
    String getUid();
}
