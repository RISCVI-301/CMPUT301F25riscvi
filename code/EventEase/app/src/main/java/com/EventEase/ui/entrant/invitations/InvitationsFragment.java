package com.EventEase.ui.entrant.invitations;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.eventease.R;

/**
 * Entrant — Invitations screen (placeholder).
 * No logic here by design (Step 0 baseline).
 */
public class InvitationsFragment extends Fragment {

    public InvitationsFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_invitations, container, false);
    }
}
