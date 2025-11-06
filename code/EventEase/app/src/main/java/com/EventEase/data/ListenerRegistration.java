package com.EventEase.data;

/** Minimal handle to unregister listeners (kept local to avoid forcing Firestore right now). */
public interface ListenerRegistration {
    void remove();
}
