package com.example.eventease.data;

/**
 * Listener interface for waitlist count updates.
 * Notifies when the waitlist count changes for an event.
 */
public interface WaitlistCountListener {
    void onChanged(int count);
}
