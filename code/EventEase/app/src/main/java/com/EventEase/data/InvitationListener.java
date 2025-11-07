package com.EventEase.data;

import com.EventEase.model.Invitation;
import java.util.List;

/**
 * Listener interface for invitation updates.
 * Notifies when active invitations change for a user.
 */
public interface InvitationListener {
    void onChanged(List<Invitation> activeInvitations);
}
