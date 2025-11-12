package com.example.eventease.data;

import com.google.android.gms.tasks.Task;

/**
 * Repository interface for invitation operations.
 * Provides methods to listen for invitations and accept or decline them.
 * This interface follows the Repository pattern to abstract data access operations.
 */
public interface InvitationRepository {
    /**
     * Registers a listener to receive active invitations for a user.
     * Active invitations are those with PENDING status that have not expired.
     * The listener will be called whenever invitations are added, updated, or removed.
     *
     * @param uid the unique user identifier
     * @param l the listener to receive invitation updates
     * @return a ListenerRegistration that can be used to stop listening
     */
    ListenerRegistration listenActive(String uid, InvitationListener l);

    /**
     * Accepts an invitation, moving the user from waitlist to admitted status.
     *
     * @param invitationId the unique invitation identifier
     * @param eventId the unique event identifier
     * @param uid the unique user identifier
     * @return a Task that completes when the invitation is accepted
     */
    Task<Void> accept(String invitationId, String eventId, String uid);

    /**
     * Declines an invitation.
     * The user remains on the waitlist but the invitation status is set to DECLINED.
     *
     * @param invitationId the unique invitation identifier
     * @param eventId the unique event identifier
     * @param uid the unique user identifier
     * @return a Task that completes when the invitation is declined
     */
    Task<Void> decline(String invitationId, String eventId, String uid);
}
