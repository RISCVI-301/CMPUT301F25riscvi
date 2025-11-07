package com.example.eventease.ui.entrant.profile;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.example.eventease.R;
import com.example.eventease.util.ToastUtil;
import java.util.ArrayList;
import java.util.List;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

/**
 * Fragment for displaying user account information.
 * Shows profile details and provides navigation to edit profile and logout.
 */
public class AccountFragment extends Fragment {
    private TextView fullNameText;
    private ShapeableImageView profileImage;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    public AccountFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.entrant_fragment_account, container, false);
        
        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        fullNameText = root.findViewById(R.id.fullNameText);
        profileImage = root.findViewById(R.id.profileImage);

        // Set up click listeners
        root.findViewById(R.id.notificationsCard).setOnClickListener(v -> {
            // Handle notifications click
        });

        root.findViewById(R.id.applyOrganizerCard).setOnClickListener(v -> {
            // Handle apply for organizer click
        });

        root.findViewById(R.id.logoutButton).setOnClickListener(v -> logout());

        root.findViewById(R.id.deleteProfileButton).setOnClickListener(v -> showDeleteConfirmationDialog());

        root.findViewById(R.id.settingsButton).setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_accountFragment_to_editProfileFragment));
        
        // Load user data with real-time updates
        loadUserData();
        
        return root;
    }
    
    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Get the current user's document in the users collection
            DocumentReference userRef = db.collection("users").document(currentUser.getUid());
            
            // Listen for real-time updates
            userRef.addSnapshotListener((documentSnapshot, e) -> {
                if (e != null) {
                    // Handle any errors
                    return;
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    // Get and set the user's name
                    String name = documentSnapshot.getString("name");
                    if (name != null && !name.isEmpty()) {
                        fullNameText.setText(name);
                    }
                    
                    // Get and load the profile photo
                    String photoUrl = documentSnapshot.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty() && getContext() != null) {
                        // Use Glide to load and cache the image
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

    private void logout() {
        if (getContext() == null) return;

        // Sign out from Firebase
        mAuth.signOut();

        // Clear Remember Me preferences
        SharedPreferences prefs = getContext().getSharedPreferences("EventEasePrefs", Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean("rememberMe", false)
            .remove("savedUid")
            .remove("savedEmail")
            .remove("savedPassword")
            .apply();

        ToastUtil.showShort(getContext(), "Logged out successfully");

        // Hide bottom nav and top bar before navigating
        if (getActivity() != null) {
            View bottomNav = getActivity().findViewById(R.id.include_bottom);
            View topBar = getActivity().findViewById(R.id.include_top);
            if (bottomNav != null) {
                bottomNav.setVisibility(View.GONE);
            }
            if (topBar != null) {
                topBar.setVisibility(View.GONE);
            }
        }

        // Navigate to welcome screen
        try {
            if (isAdded() && getView() != null) {
                Navigation.findNavController(getView()).navigate(R.id.action_accountFragment_to_welcomeFragment);
            }
        } catch (Exception e) {
            ToastUtil.showLong(getContext(), "Navigation error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showDeleteConfirmationDialog() {
        if (getContext() == null) {
            return;
        }

        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.entrant_dialog_delete_profile_confirmation);
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

        AppCompatButton btnNo = dialog.findViewById(R.id.btnNo);
        AppCompatButton btnYes = dialog.findViewById(R.id.btnYes);

        if (btnNo != null) {
            btnNo.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnYes != null) {
            btnYes.setOnClickListener(v -> {
                dialog.dismiss();
                deleteProfile();
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

    private void deleteProfile() {
        if (getContext() == null) return;

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            ToastUtil.showLong(getContext(), "Not signed in");
            return;
        }

        String uid = currentUser.getUid();
        
        // Show loading toast
        ToastUtil.showShort(getContext(), "Deleting profile...");

        // Delete all user references in cascade
        deleteUserReferences(uid, () -> {
            // After cascade deletion, delete user document and auth account
            deleteUserDocumentAndAuth(uid);
        });
    }

    private void deleteUserReferences(String uid, Runnable onComplete) {
        // Query all events where user is in waitlist or admitted arrays
        Task<QuerySnapshot> waitlistEventsTask = db.collection("events")
            .whereArrayContains("waitlist", uid)
            .get();

        Task<QuerySnapshot> admittedEventsTask = db.collection("events")
            .whereArrayContains("admitted", uid)
            .get();

        // Delete all invitations
        Task<QuerySnapshot> invitationsTask = db.collection("invitations")
            .whereEqualTo("uid", uid)
            .get();

        // Wait for all queries to complete, then remove UID from all events
        Tasks.whenAllComplete(waitlistEventsTask, admittedEventsTask, invitationsTask)
            .addOnSuccessListener(results -> {
                List<Task<Void>> updateTasks = new ArrayList<>();

                // Remove UID from waitlist arrays
                if (waitlistEventsTask.isSuccessful() && waitlistEventsTask.getResult() != null) {
                    for (QueryDocumentSnapshot document : waitlistEventsTask.getResult()) {
                        updateTasks.add(document.getReference().update("waitlist", 
                            com.google.firebase.firestore.FieldValue.arrayRemove(uid)));
                    }
                }

                // Remove UID from admitted arrays
                if (admittedEventsTask.isSuccessful() && admittedEventsTask.getResult() != null) {
                    for (QueryDocumentSnapshot document : admittedEventsTask.getResult()) {
                        updateTasks.add(document.getReference().update("admitted", 
                            com.google.firebase.firestore.FieldValue.arrayRemove(uid)));
                    }
                }

                // Collect delete tasks from invitations results
                if (invitationsTask.isSuccessful() && invitationsTask.getResult() != null) {
                    for (QueryDocumentSnapshot document : invitationsTask.getResult()) {
                        updateTasks.add(document.getReference().delete());
                    }
                }

                // Execute all update/delete tasks
                if (!updateTasks.isEmpty()) {
                    Tasks.whenAll(updateTasks)
                        .addOnSuccessListener(aVoid -> {
                            onComplete.run();
                        })
                        .addOnFailureListener(e -> {
                            // Even if some updates fail, continue with user deletion
                            android.util.Log.e("AccountFragment", "Some cascade updates failed", e);
                            onComplete.run();
                        });
                } else {
                    // No references found, proceed with user deletion
                    onComplete.run();
                }
            })
            .addOnFailureListener(e -> {
                // Even if queries fail, try to continue with user deletion
                android.util.Log.e("AccountFragment", "Failed to query user references", e);
                onComplete.run();
            });
    }

    private void deleteUserDocumentAndAuth(String uid) {
        // Delete user document from Firestore
        DocumentReference userRef = db.collection("users").document(uid);
        userRef.delete()
            .addOnSuccessListener(aVoid -> {
                // Delete Firebase Auth account
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null) {
                    currentUser.delete()
                        .addOnSuccessListener(aVoid1 -> {
                            // Sign out and clear preferences
                            mAuth.signOut();
                            clearPreferences();
                            ToastUtil.showShort(getContext(), "Profile deleted successfully");
                            navigateToWelcome();
                        })
                        .addOnFailureListener(e -> {
                            // Even if auth deletion fails, sign out and clear preferences
                            android.util.Log.e("AccountFragment", "Failed to delete auth account", e);
                            mAuth.signOut();
                            clearPreferences();
                            ToastUtil.showShort(getContext(), "Profile deleted (some cleanup may be pending)");
                            navigateToWelcome();
                        });
                } else {
                    // User already signed out, just clear preferences
                    clearPreferences();
                    ToastUtil.showShort(getContext(), "Profile deleted successfully");
                    navigateToWelcome();
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("AccountFragment", "Failed to delete user document", e);
                ToastUtil.showLong(getContext(), "Failed to delete profile: " + e.getMessage());
            });
    }

    private void clearPreferences() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences("EventEasePrefs", Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean("rememberMe", false)
            .remove("savedUid")
            .remove("savedEmail")
            .remove("savedPassword")
            .apply();
    }

    private void navigateToWelcome() {
        try {
            // Hide bottom nav and top bar before navigating
            if (getActivity() != null) {
                View bottomNav = getActivity().findViewById(R.id.include_bottom);
                View topBar = getActivity().findViewById(R.id.include_top);
                if (bottomNav != null) {
                    bottomNav.setVisibility(View.GONE);
                }
                if (topBar != null) {
                    topBar.setVisibility(View.GONE);
                }
            }
            
            if (isAdded() && getView() != null) {
                Navigation.findNavController(getView()).navigate(R.id.action_accountFragment_to_welcomeFragment);
            }
        } catch (Exception e) {
            ToastUtil.showLong(getContext(), "Navigation error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
