package com.example.eventease.data;

import com.google.android.gms.tasks.Task;

/**
 * Repository interface for invitation operations.
 * Provides methods to listen for invitations and accept or decline them.
 */
public interface InvitationRepository {
    ListenerRegistration listenActive(String uid, InvitationListener l);
    Task<Void> accept(String invitationId, String eventId, String uid);
    Task<Void> decline(String invitationId, String eventId, String uid);
}
