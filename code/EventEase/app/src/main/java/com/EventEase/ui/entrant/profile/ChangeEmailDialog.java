package com.EventEase.ui.entrant.profile;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import com.EventEase.R;
import com.EventEase.util.ToastUtil;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

/**
 * Dialog for changing user email.
 * Handles email verification and re-authentication flow.
 */
public class ChangeEmailDialog {
    
    private Dialog dialog;
    private final Context context;
    private final FirebaseUser currentUser;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    
    /**
     * Creates and shows the change email dialog.
     * 
     * @param activity the activity
     */
    public ChangeEmailDialog(Activity activity) {
        this.context = activity;
        this.auth = FirebaseAuth.getInstance();
        this.currentUser = auth.getCurrentUser();
        this.db = FirebaseFirestore.getInstance();
        
        if (currentUser == null) {
            ToastUtil.showShort(context, "Not signed in");
            return;
        }
        if (currentUser.isAnonymous()) {
            ToastUtil.showLong(context, "Cannot change email for guest accounts");
            return;
        }
        
        dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        DialogBlurHelper.setupBlurredDialog(dialog, activity, R.layout.entrant_dialog_change_email);
        
        setupDialog();
        dialog.show();
        DialogBlurHelper.applyDialogAnimations(dialog, activity);
    }
    
    private void setupDialog() {
        if (dialog == null) return;
        TextInputLayout newEmailLayout = dialog.findViewById(R.id.newEmailLayout);
        TextInputEditText newEmailInput = dialog.findViewById(R.id.newEmailInput);

        AppCompatButton cancelButton = dialog.findViewById(R.id.btnCancel);
        AppCompatButton sendButton = dialog.findViewById(R.id.btnSend);

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        if (sendButton != null) {
            sendButton.setOnClickListener(v -> {
                if (newEmailLayout != null) newEmailLayout.setError(null);

                String newEmail = newEmailInput != null && newEmailInput.getText() != null
                    ? newEmailInput.getText().toString().trim()
                    : "";

                if (newEmail.isEmpty()) {
                    if (newEmailLayout != null) newEmailLayout.setError("Email is required");
                    return;
                }

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                    if (newEmailLayout != null) newEmailLayout.setError("Invalid email address");
                    return;
                }

                sendButton.setEnabled(false);
                sendVerifyBeforeUpdateEmail(newEmail, sendButton);
            });
        }
    }
    
    private void sendVerifyBeforeUpdateEmail(String newEmail, AppCompatButton sendButton) {
        if (dialog == null) return;
        
        currentUser.verifyBeforeUpdateEmail(newEmail)
            .addOnSuccessListener(unused -> {
                ToastUtil.showLong(context, "Verification email sent. Please confirm to complete change.");
                // Update Firestore immediately with the new email
                updateEmailInFirestore(newEmail);
                // Ensure Remember Me is set to keep user logged in after email change
                SessionManager.ensureRememberMeSet(context);
                // Dismiss dialog
                if (dialog != null) {
                    dialog.dismiss();
                }
            })
            .addOnFailureListener(e -> {
                String message = e.getMessage() == null ? "Failed" : e.getMessage();
                String errorCode = (e instanceof com.google.firebase.auth.FirebaseAuthException)
                        ? ((com.google.firebase.auth.FirebaseAuthException) e).getErrorCode()
                        : "";
                // Re-enable send button
                if (sendButton != null) {
                    sendButton.setEnabled(true);
                }
                if (message.toLowerCase().contains("recent login") || "ERROR_REQUIRES_RECENT_LOGIN".equals(errorCode)) {
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                    promptReauthThenVerify(newEmail);
                } else if ("ERROR_EMAIL_ALREADY_IN_USE".equals(errorCode)) {
                    ToastUtil.showLong(context, "Email already in use");
                    if (dialog != null) {
                        TextInputLayout newEmailLayout = dialog.findViewById(R.id.newEmailLayout);
                        if (newEmailLayout != null) {
                            newEmailLayout.setError("Email already in use");
                        }
                    }
                } else if ("ERROR_OPERATION_NOT_ALLOWED".equals(errorCode)) {
                    ToastUtil.showLong(context, "Email change not allowed. Enable Email/Password in Firebase Auth.");
                } else {
                    ToastUtil.showLong(context, "Failed to send email: " + message);
                    if (dialog != null) {
                        TextInputLayout newEmailLayout = dialog.findViewById(R.id.newEmailLayout);
                        if (newEmailLayout != null) {
                            newEmailLayout.setError("Failed to send email");
                        }
                    }
                }
            });
    }
    
    private void updateEmailInFirestore(String newEmail) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;
        
        String uid = currentUser.getUid();
        DocumentReference userRef = db.collection("users").document(uid);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("email", newEmail.trim());
        updates.put("updatedAt", System.currentTimeMillis());
        
        userRef.update(updates)
            .addOnSuccessListener(aVoid -> {
                // Email updated in Firestore successfully
                // Note: Auth email will be updated after user clicks verification link
                // syncEmailToFirestore() will ensure consistency when fragment resumes
            })
            .addOnFailureListener(e -> {
                // If update fails, syncEmailToFirestore will handle it on resume
            });
    }

    private void promptReauthThenVerify(String newEmail) {
        if (context == null || currentUser.getEmail() == null) return;
        final EditText passwordInput = new EditText(context);
        passwordInput.setHint("Current password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(context)
            .setTitle("Re-authenticate")
            .setMessage("Enter your current password to continue")
            .setView(passwordInput)
            .setNegativeButton("Cancel", (d, w) -> d.dismiss())
            .setPositiveButton("Confirm", (d, w) -> {
                String pwd = passwordInput.getText() == null ? "" : passwordInput.getText().toString();
                if (pwd.isEmpty()) {
                    ToastUtil.showShort(context, "Password required");
                    return;
                }
                com.google.firebase.auth.AuthCredential cred = EmailAuthProvider.getCredential(currentUser.getEmail(), pwd);
                currentUser.reauthenticate(cred)
                    .addOnSuccessListener(unused -> {
                        sendVerifyBeforeUpdateEmail(newEmail, null);
                    })
                    .addOnFailureListener(err -> ToastUtil.showLong(context, "Re-authentication failed"));
            })
            .show();
    }
}

