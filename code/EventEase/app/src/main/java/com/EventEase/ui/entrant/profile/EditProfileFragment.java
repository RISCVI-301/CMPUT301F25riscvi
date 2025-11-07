package com.EventEase.ui.entrant.profile;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.app.AlertDialog;
import java.io.File;
import java.io.IOException;
import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.EventEase.R;
import com.EventEase.util.ToastUtil;
import java.util.HashMap;
import java.util.Map;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

public class EditProfileFragment extends Fragment {
    private EditText nameField;
    private EditText phoneField;
    private ShapeableImageView profileImage;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private Uri selectedImageUri;
    private File photoFile;
    
    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
        new ActivityResultContracts.GetContent(),
        uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                profileImage.setImageURI(uri);
            }
        }
    );
    
    private final ActivityResultLauncher<String> requestCameraPermission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        isGranted -> {
            if (isGranted) {
                openCameraInternal();
            } else {
                ToastUtil.showShort(getContext(), "Camera permission is required to take photos");
            }
        }
    );
    
    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
        new ActivityResultContracts.TakePicture(),
        success -> {
            if (success && selectedImageUri != null) {
                profileImage.setImageURI(selectedImageUri);
            }
        }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.entrant_fragment_edit_profile, container, false);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        
        // Initialize views
        nameField = root.findViewById(R.id.editNameField);
        // email field removed from layout; change handled via button
        phoneField = root.findViewById(R.id.editPhoneField);
        profileImage = root.findViewById(R.id.profileImageEdit);
        
        // Load current user data
        loadUserData();
        
        // Set up click listeners
        root.findViewById(R.id.backButton).setOnClickListener(v -> {
            // Ensure Remember Me is set before navigating back to keep user logged in
            ensureRememberMeSet();
            navigateBackSafely();
        });
        
        root.findViewById(R.id.editProfilePicture).setOnClickListener(v -> 
            showImageSourceDialog());
        
        root.findViewById(R.id.saveButton).setOnClickListener(v -> saveChanges());
        root.findViewById(R.id.changeEmailButton).setOnClickListener(v -> showChangeEmailDialog());
        root.findViewById(R.id.changePasswordButton).setOnClickListener(v -> showChangePasswordDialog());
        
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // After user confirms the verification link, Auth email changes.
        // Keep Firestore in sync with the latest Auth email.
        syncEmailToFirestore();
        // Ensure user stays logged in after email change
        ensureUserLoggedIn();
    }
    
    private void ensureUserLoggedIn() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Reload user to ensure auth state is fresh after email change
            currentUser.reload().addOnSuccessListener(unused -> {
                // User is still authenticated, ensure Remember Me is set
                ensureRememberMeSet();
            }).addOnFailureListener(e -> {
                // If reload fails, user might have been logged out
                // Try to keep them logged in by checking if we can get the user
                if (mAuth.getCurrentUser() == null) {
                    // User was logged out, but we can't re-authenticate here
                    // This should not happen after email change, but handle gracefully
                    android.util.Log.w("EditProfileFragment", "User appears to be logged out after email change");
                } else {
                    // User still exists, ensure Remember Me is set
                    ensureRememberMeSet();
                }
            });
        }
    }
    
    private void ensureRememberMeSet() {
        // Ensure Remember Me is set to keep user logged in after email change
        // Use UID for persistence (works even if email/password changes)
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (getContext() != null && currentUser != null) {
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("EventEasePrefs", android.content.Context.MODE_PRIVATE);
            String savedUid = prefs.getString("savedUid", null);
            boolean rememberMe = prefs.getBoolean("rememberMe", false);
            
            // If Remember Me is not set or UID doesn't match, set it with current UID
            if (!rememberMe || savedUid == null || !savedUid.equals(currentUser.getUid())) {
                prefs.edit()
                    .putBoolean("rememberMe", true)
                    .putString("savedUid", currentUser.getUid())
                    .apply();
            }
        }
    }
    
    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            DocumentReference userRef = db.collection("users").document(uid);
            
            userRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Set the hints with current data
                    nameField.setHint(documentSnapshot.getString("name"));
                    phoneField.setHint(documentSnapshot.getString("phoneNumber"));
                    
                    // Load profile image
                    String photoUrl = documentSnapshot.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty() && getContext() != null) {
                        Glide.with(getContext())
                            .load(photoUrl)
                            .placeholder(R.drawable.entrant_icon)
                            .error(R.drawable.entrant_icon)
                            .into(profileImage);
                    }
                }
            });
        }
    }

    private void syncEmailToFirestore() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        currentUser.reload()
            .addOnSuccessListener(unused -> {
                String uid = currentUser.getUid();
                String authEmail = currentUser.getEmail();
                if (authEmail == null || authEmail.trim().isEmpty()) return;

                DocumentReference userRef = db.collection("users").document(uid);
                userRef.get().addOnSuccessListener(snap -> {
                    if (snap != null && snap.exists()) {
                        String dbEmail = snap.getString("email");
                        if (dbEmail == null || !authEmail.equalsIgnoreCase(dbEmail)) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("email", authEmail);
                            updates.put("updatedAt", System.currentTimeMillis());
                            userRef.update(updates);
                        }
                    } else {
                        // If doc missing, set minimal fields to avoid NPE elsewhere
                        Map<String, Object> create = new HashMap<>();
                        create.put("uid", uid);
                        create.put("email", authEmail);
                        create.put("updatedAt", System.currentTimeMillis());
                        userRef.set(create, com.google.firebase.firestore.SetOptions.merge());
                    }
                });
            });
    }
    
    private void saveChanges() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isAnonymous()) {
                ToastUtil.showLong(getContext(), "Cannot change email for guest accounts");
                return;
            }
            String uid = currentUser.getUid();
            DocumentReference userRef = db.collection("users").document(uid);
            
            // First, load current values from Firestore to use if text boxes are empty
            userRef.get().addOnSuccessListener(documentSnapshot -> {
                Map<String, Object> updates = new HashMap<>();
                
                // Get current values from Firestore
                String currentName = documentSnapshot.exists() ? documentSnapshot.getString("name") : null;
                String currentPhone = documentSnapshot.exists() ? documentSnapshot.getString("phoneNumber") : null;
                
                // Get new values from text boxes
                String newName = nameField.getText().toString().trim();
                String newPhone = phoneField.getText().toString().trim();
                
                // If text box is empty, use current value from Firestore (or null if it was null)
                if (newName.isEmpty()) {
                    // Use current name if it exists, otherwise keep it as null/empty
                    if (currentName != null && !currentName.isEmpty()) {
                        updates.put("name", currentName);
                    } else {
                        // Keep as null/empty - don't update
                    }
                } else {
                    // Use new value from text box
                    updates.put("name", newName);
                }
                
                // If phone text box is empty, use current value from Firestore (or null if it was null)
                if (newPhone.isEmpty()) {
                    // Use current phone if it exists, otherwise keep it as null/empty
                    if (currentPhone != null && !currentPhone.isEmpty()) {
                        updates.put("phoneNumber", currentPhone);
                    } else {
                        // Keep as null/empty - don't update
                    }
                } else {
                    // Use new value from text box
                    updates.put("phoneNumber", newPhone);
                }

                Runnable continueProfileUpdate = () -> {
                    // Update photo if selected
                    if (selectedImageUri != null) {
                        StorageReference storageRef = storage.getReference()
                            .child("profile_pictures")
                            .child(uid + "_" + System.currentTimeMillis() + ".jpg");
                        
                        storageRef.putFile(selectedImageUri)
                            .addOnSuccessListener(taskSnapshot -> {
                                storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                                    updates.put("photoUrl", uri.toString());
                                    updateFirestore(userRef, updates);
                                });
                            })
                        .addOnFailureListener(e -> 
                            ToastUtil.showShort(getContext(), "Failed to upload image"));
                    } else {
                        updateFirestore(userRef, updates);
                    }
                };

                continueProfileUpdate.run();
            }).addOnFailureListener(e -> {
                ToastUtil.showShort(getContext(), "Failed to load current profile data");
            });
        }
    }

    private void showChangeEmailDialog() {
        if (getContext() == null) {
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            ToastUtil.showShort(getContext(), "Not signed in");
            return;
        }
        if (currentUser.isAnonymous()) {
            ToastUtil.showLong(getContext(), "Cannot change email for guest accounts");
            return;
        }

        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.entrant_dialog_change_email);
        dialog.setCanceledOnTouchOutside(false);

        // Set window properties for full screen blur
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
            layoutParams.dimAmount = 0f;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Capture screenshot and blur it for the background
        Bitmap screenshot = captureScreenshot();
        if (screenshot != null) {
            Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
            if (blurredBitmap != null) {
                android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
                if (blurBackground != null) {
                    blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
                }
            }
        }
        
        // Make the background clickable to dismiss
        android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> dialog.dismiss());
        }

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
                sendVerifyBeforeUpdateEmail(currentUser, newEmail, dialog, sendButton);
            });
        }

        dialog.show();
        
        // Apply animations after dialog is shown
        View card = dialog.findViewById(R.id.dialogCard);
        if (blurBackground != null && card != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_zoom_in);
            
            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
        }
    }

    private void sendVerifyBeforeUpdateEmail(FirebaseUser user, String newEmail) {
        sendVerifyBeforeUpdateEmail(user, newEmail, null, null);
    }

    private void sendVerifyBeforeUpdateEmail(FirebaseUser user, String newEmail, Dialog dialog, AppCompatButton sendButton) {
        user.verifyBeforeUpdateEmail(newEmail)
            .addOnSuccessListener(unused -> {
                ToastUtil.showLong(getContext(), "Verification email sent. Please confirm to complete change.");
                // Update Firestore immediately with the new email
                // This ensures the database is updated when the email change is initiated
                // syncEmailToFirestore() in onResume() will ensure it stays in sync with Auth after verification
                updateEmailInFirestore(newEmail);
                // Ensure Remember Me is set to keep user logged in after email change
                ensureRememberMeSet();
                // Dismiss dialog if provided
                if (dialog != null) {
                    dialog.dismiss();
                }
            })
            .addOnFailureListener(e -> {
                String message = e.getMessage() == null ? "Failed" : e.getMessage();
                String errorCode = (e instanceof com.google.firebase.auth.FirebaseAuthException)
                        ? ((com.google.firebase.auth.FirebaseAuthException) e).getErrorCode()
                        : "";
                // Re-enable send button if dialog is provided
                if (sendButton != null) {
                    sendButton.setEnabled(true);
                }
                if (message.toLowerCase().contains("recent login") || "ERROR_REQUIRES_RECENT_LOGIN".equals(errorCode)) {
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                    promptReauthThenVerify(user, newEmail);
                } else if ("ERROR_EMAIL_ALREADY_IN_USE".equals(errorCode)) {
                    ToastUtil.showLong(getContext(), "Email already in use");
                    if (dialog != null) {
                        TextInputLayout newEmailLayout = dialog.findViewById(R.id.newEmailLayout);
                        if (newEmailLayout != null) {
                            newEmailLayout.setError("Email already in use");
                        }
                    }
                } else if ("ERROR_OPERATION_NOT_ALLOWED".equals(errorCode)) {
                    ToastUtil.showLong(getContext(), "Email change not allowed. Enable Email/Password in Firebase Auth.");
                } else {
                    ToastUtil.showLong(getContext(), "Failed to send email: " + message);
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
        FirebaseUser currentUser = mAuth.getCurrentUser();
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

    private void promptReauthThenVerify(FirebaseUser user, String newEmail) {
        if (getContext() == null || user.getEmail() == null) return;
        final EditText passwordInput = new EditText(getContext());
        passwordInput.setHint("Current password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(requireContext())
            .setTitle("Re-authenticate")
            .setMessage("Enter your current password to continue")
            .setView(passwordInput)
            .setNegativeButton("Cancel", (d, w) -> d.dismiss())
            .setPositiveButton("Confirm", (d, w) -> {
                String pwd = passwordInput.getText() == null ? "" : passwordInput.getText().toString();
                if (pwd.isEmpty()) {
                    ToastUtil.showShort(getContext(), "Password required");
                    return;
                }
                AuthCredential cred = EmailAuthProvider.getCredential(user.getEmail(), pwd);
                user.reauthenticate(cred)
                    .addOnSuccessListener(unused -> {
                        sendVerifyBeforeUpdateEmail(user, newEmail);
                    })
                    .addOnFailureListener(err -> ToastUtil.showLong(getContext(), "Re-authentication failed"));
            })
            .show();
    }
    
    private void updateFirestore(DocumentReference userRef, Map<String, Object> updates) {
        if (!updates.isEmpty()) {
            updates.put("updatedAt", System.currentTimeMillis());
            userRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    ToastUtil.showShort(getContext(), "Profile updated successfully");
                    navigateBackSafely();
                })
                .addOnFailureListener(e -> 
                    ToastUtil.showShort(getContext(), "Failed to update profile"));
        } else {
            // Even if no updates, navigate back
            navigateBackSafely();
        }
    }
    
    private void navigateBackSafely() {
        View view = getView();
        if (view != null) {
            try {
                Navigation.findNavController(view).navigateUp();
            } catch (Exception e) {
                // Fallback: try to navigate back using the activity
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            }
        }
    }

    private void showChangePasswordDialog() {
        if (getContext() == null) {
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            ToastUtil.showShort(getContext(), "Unable to change password right now");
            return;
        }

        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.entrant_dialog_change_password);
        dialog.setCanceledOnTouchOutside(false);

        // Set window properties for full screen blur
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
            layoutParams.dimAmount = 0f;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Capture screenshot and blur it for the background
        Bitmap screenshot = captureScreenshot();
        if (screenshot != null) {
            Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
            if (blurredBitmap != null) {
                android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
                if (blurBackground != null) {
                    blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
                }
            }
        }
        
        // Make the background clickable to dismiss
        android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> dialog.dismiss());
        }

        TextInputLayout currentPasswordLayout = dialog.findViewById(R.id.currentPasswordLayout);
        TextInputLayout newPasswordLayout = dialog.findViewById(R.id.newPasswordLayout);
        TextInputLayout confirmPasswordLayout = dialog.findViewById(R.id.confirmNewPasswordLayout);

        TextInputEditText currentPasswordInput = dialog.findViewById(R.id.currentPasswordInput);
        TextInputEditText newPasswordInput = dialog.findViewById(R.id.newPasswordInput);
        TextInputEditText confirmPasswordInput = dialog.findViewById(R.id.confirmNewPasswordInput);

        AppCompatButton cancelButton = dialog.findViewById(R.id.btnCancel);
        AppCompatButton saveButton = dialog.findViewById(R.id.btnSaveChanges);

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
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

                AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);

                currentUser.reauthenticate(credential)
                    .addOnSuccessListener(unused -> currentUser.updatePassword(newPassword)
                        .addOnSuccessListener(updateUnused -> {
                            ToastUtil.showShort(getContext(), "Password updated successfully");
                            dialog.dismiss();
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

        dialog.show();
        
        // Apply animations after dialog is shown
        View card = dialog.findViewById(R.id.dialogCard);
        if (blurBackground != null && card != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_zoom_in);
            
            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
        }
    }
    
    private Bitmap captureScreenshot() {
        try {
            if (getActivity() == null || getActivity().getWindow() == null) return null;
            android.view.View rootView = getActivity().getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private Bitmap blurBitmap(Bitmap bitmap, float radius) {
        if (bitmap == null || getContext() == null) return null;
        
        try {
            // Scale down for better performance
            int width = Math.round(bitmap.getWidth() * 0.4f);
            int height = Math.round(bitmap.getHeight() * 0.4f);
            Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);
            
            RenderScript rs = RenderScript.create(getContext());
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
            
            blurScript.setRadius(radius);
            blurScript.setInput(tmpIn);
            blurScript.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);
            
            rs.destroy();
            
            // Scale back up
            return Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), true);
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }
    
    private void showImageSourceDialog() {
        if (getContext() == null) return;
        
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.entrant_dialog_image_source);
        dialog.setCanceledOnTouchOutside(false);

        // Set window properties for full screen blur
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
            layoutParams.dimAmount = 0f;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Capture screenshot and blur it for the background
        Bitmap screenshot = captureScreenshot();
        if (screenshot != null) {
            Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
            if (blurredBitmap != null) {
                android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
                if (blurBackground != null) {
                    blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
                }
            }
        }
        
        // Make the background clickable to dismiss
        android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> dialog.dismiss());
        }

        AppCompatButton cameraButton = dialog.findViewById(R.id.btnCamera);
        AppCompatButton galleryButton = dialog.findViewById(R.id.btnGallery);

        if (cameraButton != null) {
            cameraButton.setOnClickListener(v -> {
                dialog.dismiss();
                openCamera();
            });
        }

        if (galleryButton != null) {
            galleryButton.setOnClickListener(v -> {
                dialog.dismiss();
                pickImage.launch("image/*");
            });
        }

        dialog.show();
        
        // Apply animations after dialog is shown
        View card = dialog.findViewById(R.id.dialogCard);
        if (blurBackground != null && card != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_zoom_in);
            
            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
        }
    }
    
    private void openCamera() {
        if (getContext() == null) return;
        
        // Check camera permission first
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            // Request camera permission
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        } else {
            // Permission already granted, open camera
            openCameraInternal();
        }
    }
    
    private void openCameraInternal() {
        if (getContext() == null) return;
        
        try {
            // Create a File object for the photo
            photoFile = File.createTempFile(
                "profile_photo_" + System.currentTimeMillis(),
                ".jpg",
                getContext().getCacheDir()
            );
            
            // Create a content URI for the file using FileProvider
            selectedImageUri = FileProvider.getUriForFile(
                getContext(),
                getContext().getPackageName() + ".fileprovider",
                photoFile
            );
            
            // Launch the camera intent
            takePicture.launch(selectedImageUri);
        } catch (IOException e) {
            e.printStackTrace();
            ToastUtil.showShort(getContext(), "Failed to create image file");
        } catch (Exception e) {
            e.printStackTrace();
            ToastUtil.showShort(getContext(), "Failed to open camera: " + e.getMessage());
        }
    }
}
