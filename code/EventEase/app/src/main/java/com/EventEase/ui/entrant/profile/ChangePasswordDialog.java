package com.EventEase.ui.entrant.profile;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import androidx.appcompat.widget.AppCompatButton;
import com.EventEase.R;
import com.EventEase.util.ToastUtil;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Dialog for changing user password.
 */
public class ChangePasswordDialog {
    
    private Dialog dialog;
    private final Context context;
    private final FirebaseUser currentUser;
    
    /**
     * Creates and shows the change password dialog.
     * 
     * @param activity the activity
     */
    public ChangePasswordDialog(Activity activity) {
        this.context = activity;
        this.currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser == null || currentUser.getEmail() == null) {
            ToastUtil.showShort(context, "Unable to change password right now");
            return;
        }
        
        dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        DialogBlurHelper.setupBlurredDialog(dialog, activity, R.layout.entrant_dialog_change_password);
        
        setupDialog();
        dialog.show();
        DialogBlurHelper.applyDialogAnimations(dialog, activity);
    }
    
    private void setupDialog() {
        if (dialog == null) return;
        TextInputLayout currentPasswordLayout = dialog.findViewById(R.id.currentPasswordLayout);
        TextInputLayout newPasswordLayout = dialog.findViewById(R.id.newPasswordLayout);
        TextInputLayout confirmPasswordLayout = dialog.findViewById(R.id.confirmNewPasswordLayout);

        TextInputEditText currentPasswordInput = dialog.findViewById(R.id.currentPasswordInput);
        TextInputEditText newPasswordInput = dialog.findViewById(R.id.newPasswordInput);
        TextInputEditText confirmPasswordInput = dialog.findViewById(R.id.confirmNewPasswordInput);

        AppCompatButton cancelButton = dialog.findViewById(R.id.btnCancel);
        AppCompatButton saveButton = dialog.findViewById(R.id.btnSaveChanges);

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> {
                if (dialog != null) {
                    dialog.dismiss();
                }
            });
        }

        if (saveButton != null) {
            saveButton.setOnClickListener(v -> {
                if (currentPasswordLayout != null) currentPasswordLayout.setError(null);
                if (newPasswordLayout != null) newPasswordLayout.setError(null);
                if (confirmPasswordLayout != null) confirmPasswordLayout.setError(null);

                String currentPassword = currentPasswordInput != null && currentPasswordInput.getText() != null
                    ? currentPasswordInput.getText().toString().trim()
                    : "";
                String newPassword = newPasswordInput != null && newPasswordInput.getText() != null
                    ? newPasswordInput.getText().toString().trim()
                    : "";
                String confirmPassword = confirmPasswordInput != null && confirmPasswordInput.getText() != null
                    ? confirmPasswordInput.getText().toString().trim()
                    : "";

                boolean hasError = false;

                if (currentPassword.isEmpty()) {
                    if (currentPasswordLayout != null) currentPasswordLayout.setError("Current password required");
                    hasError = true;
                }

                if (newPassword.isEmpty()) {
                    if (newPasswordLayout != null) newPasswordLayout.setError("New password required");
                    hasError = true;
                } else if (newPassword.length() < 6) {
                    if (newPasswordLayout != null) newPasswordLayout.setError("Password must be at least 6 characters");
                    hasError = true;
                } else if (newPassword.equals(currentPassword)) {
                    if (newPasswordLayout != null) newPasswordLayout.setError("Use a different password");
                    hasError = true;
                }

                if (!newPassword.equals(confirmPassword)) {
                    if (confirmPasswordLayout != null) confirmPasswordLayout.setError("Passwords do not match");
                    hasError = true;
                }

                if (hasError) {
                    return;
                }

                saveButton.setEnabled(false);

                com.google.firebase.auth.AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);

                currentUser.reauthenticate(credential)
                    .addOnSuccessListener(unused -> currentUser.updatePassword(newPassword)
                        .addOnSuccessListener(updateUnused -> {
                            ToastUtil.showShort(context, "Password updated successfully");
                            if (dialog != null) {
                                dialog.dismiss();
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (newPasswordLayout != null) newPasswordLayout.setError("Failed to update password");
                            saveButton.setEnabled(true);
                        }))
                    .addOnFailureListener(e -> {
                        if (currentPasswordLayout != null) currentPasswordLayout.setError("Current password is incorrect");
                        saveButton.setEnabled(true);
                    });
            });
        }
    }
}

