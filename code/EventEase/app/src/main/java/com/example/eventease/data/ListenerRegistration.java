package com.example.eventease.data;

/**
 * Interface for unregistering listeners.
 * 
 * <p>Provides a handle to remove active listeners when they are no longer needed.
 * This interface is used by repository classes that provide real-time data updates through
 * Firestore listeners. Calling remove() stops the listener and prevents memory leaks.
 * 
 * <p>Implementations should ensure that calling remove() is idempotent (safe to call multiple times).
 */
public interface ListenerRegistration {
    /**
     * Removes the listener and stops receiving updates.
     * This method should be called when the listener is no longer needed, typically in
     * onStop(), onDestroy(), or when the fragment/activity is being destroyed.
     */
    void remove();
}
