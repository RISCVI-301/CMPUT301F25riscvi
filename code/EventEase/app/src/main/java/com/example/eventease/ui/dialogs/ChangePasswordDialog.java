package com.example.eventease.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.eventease.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordDialog extends Dialog {
    private TextInputEditText currentPasswordInput;
    private TextInputEditText newPasswordInput;
    private TextInputEditText confirmNewPasswordInput;
    private Button btnCancel;
    private Button btnSaveChanges;

    public ChangePasswordDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_change_password);

        // Initialize views
        currentPasswordInput = findViewById(R.id.currentPasswordInput);
        newPasswordInput = findViewById(R.id.newPasswordInput);
        confirmNewPasswordInput = findViewById(R.id.confirmNewPasswordInput);
        btnCancel = findViewById(R.id.btnCancel);
        btnSaveChanges = findViewById(R.id.btnSaveChanges);

        // Set up click listeners
        btnCancel.setOnClickListener(v -> dismiss());
        btnSaveChanges.setOnClickListener(v -> validateAndChangePassword());
    }

    private void validateAndChangePassword() {
        String currentPassword = currentPasswordInput.getText().toString().trim();
        String newPassword = newPasswordInput.getText().toString().trim();
        String confirmNewPassword = confirmNewPasswordInput.getText().toString().trim();

        // Validate inputs
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmNewPassword.isEmpty()) {
            showToast("Please fill in all fields");
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            showToast("New passwords do not match");
            return;
        }

        if (newPassword.length() < 6) {
            showToast("New password must be at least 6 characters long");
            return;
        }

        // Get current user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showToast("No user is currently signed in");
            return;
        }

        // Re-authenticate user before changing password
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    // Re-authentication successful, now change password
                    user.updatePassword(newPassword)
                            .addOnSuccessListener(aVoid1 -> {
                                showToast("Password updated successfully");
                                dismiss();
                            })
                            .addOnFailureListener(e -> showToast("Failed to update password: " + e.getMessage()));
                })
                .addOnFailureListener(e -> showToast("Current password is incorrect"));
    }

    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
}