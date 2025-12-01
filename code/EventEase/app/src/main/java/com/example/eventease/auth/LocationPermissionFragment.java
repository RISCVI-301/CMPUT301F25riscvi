package com.example.eventease.auth;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.eventease.R;
import com.example.eventease.notifications.FCMTokenManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Fragment for requesting location permissions during signup.
 * Handles permission requests and location access setup.
 */
public class LocationPermissionFragment extends Fragment {

    private static final String PREFS_NAME = "EventEasePrefs";
    private static final String KEY_LOCATION_PERMISSION = "locationPermission";
    private static final String PERMISSION_ONCE = "once";
    private static final String PERMISSION_WHILE_USING = "whileUsing";
    private static final String PERMISSION_DENIED = "denied";

    private Button btnAllowPermission;
    private ProgressBar progressBar;

    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private String selectedPermissionType;
    private static final String TAG = "LocationPermissionFragment";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize location permission launcher
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLocationGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarseLocationGranted = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);

                    if (Boolean.TRUE.equals(fineLocationGranted) || Boolean.TRUE.equals(coarseLocationGranted)) {
                        // Permission granted - determine type based on what user selected in system dialog
                        // If user selected "While using the app" in system dialog, use that
                        // Note: We can't detect which option user chose, so we'll default to "whileUsing"
                        selectedPermissionType = PERMISSION_WHILE_USING;
                        savePermissionPreference(selectedPermissionType);
                        // Request notification permission immediately after location permission is granted
                        // Location will be captured after notification permission is handled
                        requestNotificationPermission();
                    } else {
                        // Permission denied
                        savePermissionPreference(PERMISSION_DENIED);
                        // Still request notification permission even if location was denied
                        requestNotificationPermission();
                    }
                });
        
        // Initialize notification permission launcher
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "Notification permission granted on first sign-in");
                        // Initialize FCM after permission is granted
                        if (getContext() != null) {
                            FCMTokenManager.getInstance().initialize(getContext());
                        }
                    } else {
                        Log.w(TAG, "Notification permission denied on first sign-in");
                        // Still initialize FCM - may work on older Android versions
                        if (getContext() != null) {
                            FCMTokenManager.getInstance().initialize(getContext());
                        }
                    }
                    // Capture location if permission was granted, then navigate to discover
                    if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        captureAndSaveLocation();
                    } else {
                        // Location permission was denied, just navigate to discover
                    navigateToDiscover();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.entrant_fragment_location_permission, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase and Location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        db = FirebaseFirestore.getInstance();

        // Initialize views
        btnAllowPermission = view.findViewById(R.id.btnAllowPermission);
        progressBar = view.findViewById(R.id.progressBar);

        // Set click listener
        btnAllowPermission.setOnClickListener(v -> handleAllowPermission());
    }

    private void handleAllowPermission() {
        // Set permission type to "while using app" as default
        selectedPermissionType = PERMISSION_WHILE_USING;
        // Request location permission first, then notification permission will be requested after location is handled
        requestLocationPermission();
    }

    private void requestLocationPermission() {
        // Check if permission is already granted
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted - still request notification permission first
            selectedPermissionType = PERMISSION_WHILE_USING;
            savePermissionPreference(selectedPermissionType);
            // Request notification permission, location will be captured after notification permission is handled
            requestNotificationPermission();
        } else {
            // Request permission
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void captureAndSaveLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Location permission not granted", Toast.LENGTH_SHORT).show();
            navigateToDiscover();
            return;
        }

        setLoading(true);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        saveLocationToFirestore(location);
                    } else {
                        // Try to get current location
                        requestNewLocation();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(requireContext(), "Failed to get location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    navigateToDiscover();
                });
    }

    private void requestNewLocation() {
        // Fallback: Try using LocationManager
        try {
            LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                
                if (lastKnownLocation != null) {
                    saveLocationToFirestore(lastKnownLocation);
                } else {
                    setLoading(false);
                    Toast.makeText(requireContext(), "Unable to get location. Please enable location services.", Toast.LENGTH_SHORT).show();
                    navigateToDiscover();
                }
            }
        } catch (Exception e) {
            setLoading(false);
            Toast.makeText(requireContext(), "Error getting location", Toast.LENGTH_SHORT).show();
            navigateToDiscover();
        }
    }

    private void saveLocationToFirestore(Location location) {
        com.example.eventease.auth.DeviceAuthManager authManager = 
            new com.example.eventease.auth.DeviceAuthManager(requireContext());
        String uid = authManager.getUid();
        if (uid == null) {
            setLoading(false);
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show();
            navigateToDiscover();
            return;
        }

        // Create location data
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", location.getLatitude());
        locationData.put("longitude", location.getLongitude());
        locationData.put("timestamp", System.currentTimeMillis());

        Map<String, Object> updates = new HashMap<>();
        updates.put("location", locationData);
        updates.put("locationPermission", selectedPermissionType);

        db.collection("users").document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    Toast.makeText(requireContext(), "Location saved successfully!", Toast.LENGTH_SHORT).show();
                    // Navigate to discover after location is saved
                    navigateToDiscover();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "Failed to save location: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), "Failed to save location: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Navigate to discover even if location save failed
                    navigateToDiscover();
                });
    }
    
    /**
     * Requests notification permission for Android 13+ (API 33+).
     * Called after location permission is handled.
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires runtime permission
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting notification permission on first sign-in");
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                Log.d(TAG, "Notification permission already granted");
                // Initialize FCM and navigate
                if (getContext() != null) {
                    FCMTokenManager.getInstance().initialize(getContext());
                }
                navigateToDiscover();
            }
        } else {
            // Android 12 and below - permission granted via manifest
            Log.d(TAG, "Android version < 13, notification permission granted via manifest");
            if (getContext() != null) {
                FCMTokenManager.getInstance().initialize(getContext());
            }
            navigateToDiscover();
        }
    }

    private void savePermissionPreference(String permissionType) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOCATION_PERMISSION, permissionType).apply();
    }

    private void navigateToDiscover() {
        try {
            if (isAdded() && getView() != null) {
                NavHostFragment.findNavController(this).navigate(R.id.action_locationPermission_to_discover);
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Navigation error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnAllowPermission != null) {
            btnAllowPermission.setEnabled(!loading);
        }
    }
}

