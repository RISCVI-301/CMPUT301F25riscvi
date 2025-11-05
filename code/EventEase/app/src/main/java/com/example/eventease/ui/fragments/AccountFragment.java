package com.example.eventease.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.eventease.R;
import com.example.eventease.ui.dialogs.ChangePasswordDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AccountFragment extends Fragment {
    private TextView fullNameText;
    private ImageView profileImage;
    private ImageView settingsButton;
    private CardView notificationsCard;
    private CardView applyOrganizerCard;
    private CardView changePasswordButton;
    private CardView deleteProfileButton;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                         @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.entrant_fragment_account, container, false);

        // Initialize views
        fullNameText = view.findViewById(R.id.fullNameText);
        profileImage = view.findViewById(R.id.profileImage);
        settingsButton = view.findViewById(R.id.settingsButton);
        notificationsCard = view.findViewById(R.id.notificationsCard);
        applyOrganizerCard = view.findViewById(R.id.applyOrganizerCard);
        changePasswordButton = view.findViewById(R.id.changePasswordButton);
        deleteProfileButton = view.findViewById(R.id.deleteProfileButton);

        // Set up click listeners
        setupClickListeners();

        // Load user data
        loadUserData();

        return view;
    }

    private void setupClickListeners() {
        changePasswordButton.setOnClickListener(v -> showChangePasswordDialog());

        // Add other click listeners here
    }

    private void showChangePasswordDialog() {
        ChangePasswordDialog dialog = new ChangePasswordDialog(requireContext());
        dialog.show();
    }

    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Set user's name
            String displayName = user.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                fullNameText.setText(displayName);
            }

            // Load profile image if available
            // TODO: Implement profile image loading
        }
    }
}