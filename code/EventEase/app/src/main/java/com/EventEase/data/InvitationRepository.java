package com.EventEase.data;

import com.google.android.gms.tasks.Task;

public interface InvitationRepository {
    ListenerRegistration listenActive(String uid, InvitationListener l);
    Task<Void> accept(String invitationId, String eventId, String uid);
    Task<Void> decline(String invitationId, String eventId, String uid);
}
