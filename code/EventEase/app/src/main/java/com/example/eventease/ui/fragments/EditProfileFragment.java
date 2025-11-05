package com.example.eventease.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.eventease.R;
import com.example.eventease.ui.dialogs.ChangePasswordDialog;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EditProfileFragment extends Fragment {
    private ImageButton backButton;
    private ShapeableImageView profileImageEdit;
    private ImageView editProfilePicture;
    private EditText editNameField;
    private EditText editPhoneField;
    private CardView changePasswordButton;
    private CardView saveButton;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.entrant_fragment_edit_profile, container, false);

        // Initialize views
        initializeViews(view);
        
        // Set up click listeners
        setupClickListeners();

        // Load user data
        loadUserData();

        return view;
    }

    private void initializeViews(View view) {
        backButton = view.findViewById(R.id.backButton);
        profileImageEdit = view.findViewById(R.id.profileImageEdit);
        editProfilePicture = view.findViewById(R.id.editProfilePicture);
        editNameField = view.findViewById(R.id.editNameField);
        editPhoneField = view.findViewById(R.id.editPhoneField);
        changePasswordButton = view.findViewById(R.id.changePasswordButton);
        saveButton = view.findViewById(R.id.saveButton);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> requireActivity().onBackPressed());

        changePasswordButton.setOnClickListener(v -> showChangePasswordDialog());

        saveButton.setOnClickListener(v -> saveChanges());

        editProfilePicture.setOnClickListener(v -> handleProfilePictureChange());
    }

    private void showChangePasswordDialog() {
        ChangePasswordDialog dialog = new ChangePasswordDialog(requireContext());
        dialog.show();
    }

    private void saveChanges() {
        // TODO: Implement save changes functionality
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String newName = editNameField.getText().toString().trim();
            String newPhone = editPhoneField.getText().toString().trim();
            // Update user profile
            // ...
        }
    }

    private void handleProfilePictureChange() {
        // TODO: Implement profile picture change functionality
    }

    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Load user data
            String name = user.getDisplayName();
            String email = user.getEmail();
            String phone = user.getPhoneNumber();

            if (name != null) editNameField.setText(name);
            // email field removed from layout; email change is handled via separate flow
            if (phone != null) editPhoneField.setText(phone);

            // TODO: Load profile picture
        }
    }
}