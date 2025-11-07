package com.EventEase.auth;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
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
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.EventEase.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
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

    private Button btnAllowOnce;
    private Button btnWhileUsingApp;
    private Button btnDontAllow;
    private ProgressBar progressBar;

    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private String selectedPermissionType;

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
                        // Permission granted
                        savePermissionPreference(selectedPermissionType);
                        captureAndSaveLocation();
                    } else {
                        // Permission denied
                        savePermissionPreference(PERMISSION_DENIED);
                        Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
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
        auth = FirebaseAuth.getInstance();

        // Initialize views
        btnAllowOnce = view.findViewById(R.id.btnAllowOnce);
        btnWhileUsingApp = view.findViewById(R.id.btnWhileUsingApp);
        btnDontAllow = view.findViewById(R.id.btnDontAllow);
        progressBar = view.findViewById(R.id.progressBar);

        // Set click listeners
        btnAllowOnce.setOnClickListener(v -> handleAllowOnce());
        btnWhileUsingApp.setOnClickListener(v -> handleWhileUsingApp());
        btnDontAllow.setOnClickListener(v -> handleDontAllow());
    }

    private void handleAllowOnce() {
        selectedPermissionType = PERMISSION_ONCE;
        requestLocationPermission();
    }

    private void handleWhileUsingApp() {
        selectedPermissionType = PERMISSION_WHILE_USING;
        requestLocationPermission();
    }

    private void handleDontAllow() {
        selectedPermissionType = PERMISSION_DENIED;
        savePermissionPreference(PERMISSION_DENIED);
        
        // Save null location to Firestore
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("location", null);
            updates.put("locationPermission", PERMISSION_DENIED);
            
            db.collection("users").document(uid)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> navigateToDiscover())
                    .addOnFailureListener(e -> {
                        Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        navigateToDiscover();
                    });
        } else {
            navigateToDiscover();
        }
    }

    private void requestLocationPermission() {
        // Check if permission is already granted
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted
            savePermissionPreference(selectedPermissionType);
            captureAndSaveLocation();
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
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
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
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    Toast.makeText(requireContext(), "Location saved successfully!", Toast.LENGTH_SHORT).show();
                    navigateToDiscover();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(requireContext(), "Failed to save location: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    navigateToDiscover();
                });
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
        btnAllowOnce.setEnabled(!loading);
        btnWhileUsingApp.setEnabled(!loading);
        btnDontAllow.setEnabled(!loading);
    }
}

