package com.example.eventease.admin.profile.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.profile.data.AdminProfileDatabaseController;
import com.example.eventease.admin.profile.data.UserProfile;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.widget.Button;
import android.util.Log;
import com.example.eventease.MainActivity;

/**
 * Fragment for admin users to view and manage all user profiles in the system.
 * 
 * <p>This fragment displays a list of all user profiles from the Firestore database and provides
 * administrative functionality to view and delete user profiles. Admins can see all users
 * regardless of their role (entrant, organizer, admin).
 * 
 * <p>Features:
 * <ul>
 *   <li>View all user profiles in a scrollable list</li>
 *   <li>Delete user profiles with confirmation dialog</li>
 *   <li>Prevent self-deletion (admin cannot delete their own profile)</li>
 *   <li>Refresh profile list after deletion</li>
 * </ul>
 * 
 * <p>The fragment uses AdminProfileDatabaseController to fetch profiles and ProfileDeletionHelper
 * to perform comprehensive profile deletions (including associated data). Only users with admin
 * role can access this functionality.
 */
public class AdminProfilesFragment extends Fragment {

    private final AdminProfileDatabaseController APDC = new AdminProfileDatabaseController();
    private RecyclerView rv;
    private AdminProfileAdapter adapter;
    private List<UserProfile> profiles = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.admin_profiles_management, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button switchButton = view.findViewById(R.id.btnSwitchToEntrantView);
        if (switchButton != null) {
            switchButton.setOnClickListener(v -> {
                android.util.Log.d("AdminToEntrant", "Admin to Entrant Clicked");
                Intent intent = new Intent(requireContext(), com.example.eventease.MainActivity.class);
                intent.putExtra("force_entrant", true);
                startActivity(intent);
                requireActivity().finish();
            });
        }

        rv = view.findViewById(R.id.rvProfiles);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new AdminProfileAdapter(requireContext(), new ArrayList<>(), this::deleteProfileAndRefresh);
            rv.setAdapter(adapter);
        }

        // Load profiles
        loadProfiles();
    }

    private void loadProfiles() {
        APDC.fetchProfiles(new AdminProfileDatabaseController.ProfilesCallback() {
            @Override
            public void onLoaded(@NonNull List<UserProfile> data) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (adapter != null && rv != null) {
                            profiles = data;
                            adapter.submitList(data);
                        }
                    });
                }
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        String errorMsg = e.getMessage();
                        String className = e.getClass().getSimpleName();
                        
                        // Log full error details
                        android.util.Log.e("AdminProfiles", "Error loading profiles. Type: " + className + ", Message: " + errorMsg, e);
                        
                        if (errorMsg != null) {
                            // Check for specific error types
                            if (errorMsg.contains("Permission denied") || errorMsg.contains("permission")) {
                                Toast.makeText(requireContext(), 
                                    "Permission denied. Please verify:\n1. Firestore rules are deployed\n2. Your user has 'admin' role\n3. Check Logcat for details", 
                                    Toast.LENGTH_LONG).show();
                            } else if (errorMsg.contains("Failed to verify admin status")) {
                                Toast.makeText(requireContext(), 
                                    "Cannot verify admin status. Check Logcat for details.", 
                                    Toast.LENGTH_LONG).show();
                            } else if (errorMsg.contains("Only administrators")) {
                                Toast.makeText(requireContext(), 
                                    "Access denied: Admin role required", 
                                    Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(requireContext(), 
                                    "Error loading profiles: " + errorMsg, 
                                    Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(requireContext(), 
                                "Error loading profiles. Check Logcat for details.", 
                                Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private void deleteProfileAndRefresh(@NonNull UserProfile profile) {
        // Check if trying to delete current device's profile
        com.example.eventease.auth.DeviceAuthManager authManager = 
            new com.example.eventease.auth.DeviceAuthManager(requireContext());
        String deviceId = authManager.getUid();
        if (deviceId != null && profile.getUid() != null && profile.getUid().equals(deviceId)) {
            Toast.makeText(requireContext(), "You cannot delete your own profile", Toast.LENGTH_LONG).show();
            return;
        }
        
        // Show confirmation dialog before deleting
        String profileName = profile.getName() != null && !profile.getName().isEmpty() 
            ? profile.getName() 
            : profile.getEmail() != null && !profile.getEmail().isEmpty() 
                ? profile.getEmail() 
                : "this user";
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Profile")
            .setMessage("Are you sure you want to delete " + profileName + "? This will remove all their data including events, invitations, and profile information. This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                // Show loading indicator
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Deleting profile...", Toast.LENGTH_SHORT).show();
                }
                
                // Delete the profile
                APDC.deleteProfile(requireContext(), profile, new AdminProfileDatabaseController.DeleteCallback() {
                    @Override
                    public void onSuccess() {
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "Profile deleted successfully", Toast.LENGTH_SHORT).show();
                                loadProfiles();
                            });
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "Error deleting profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

}

