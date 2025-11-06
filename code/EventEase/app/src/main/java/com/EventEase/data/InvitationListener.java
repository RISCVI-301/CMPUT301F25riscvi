package com.EventEase.data;

import com.EventEase.model.Invitation;
import java.util.List;

public interface InvitationListener {
    void onChanged(List<Invitation> activeInvitations);
}
