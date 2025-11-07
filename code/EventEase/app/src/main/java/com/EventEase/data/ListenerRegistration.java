package com.EventEase.data;

/**
 * Interface for unregistering listeners.
 * Provides a handle to remove active listeners when no longer needed.
 */
public interface ListenerRegistration {
    void remove();
}
