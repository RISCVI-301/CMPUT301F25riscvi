package com.example.eventease.auth;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.eventease.MainActivity;
import com.example.eventease.R;
import com.example.eventease.notifications.FCMTokenManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Activity for requesting location and notification permissions after profile setup.
 * This is shown once after a user creates their profile for the first time.
 */
public class PermissionRequestActivity extends AppCompatActivity {
    private static final String TAG = "PermissionRequestActivity";
    private static final String PREFS_NAME = "EventEasePrefs";
    private static final String KEY_LOCATION_PERMISSION = "locationPermission";
    private static final String PERMISSION_ONCE = "once";
    private static final String PERMISSION_WHILE_USING = "whileUsing";
    private static final String PERMISSION_DENIED = "denied";
    
    private TextView tvTitle;
    private TextView tvDescription;
    private Button btnAllowPermission;
    private ProgressBar progressBar;
    
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;
    private DeviceAuthManager authManager;
    private String selectedPermissionType;
    
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_request);
        
        // Hide action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        authManager = new DeviceAuthManager(this);
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Initialize views
        tvTitle = findViewById(R.id.tvTitle);
        tvDescription = findViewById(R.id.tvDescription);
        btnAllowPermission = findViewById(R.id.btnAllowPermission);
        progressBar = findViewById(R.id.progressBar);
        
        // Set up title and description
        if (tvTitle != null) {
            tvTitle.setText("Enable Location & Notifications");
        }
        if (tvDescription != null) {
            tvDescription.setText("We need your location and notification permissions to provide the best experience.");
        }
        
        // Initialize permission launchers
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLocationGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarseLocationGranted = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    
                    if (Boolean.TRUE.equals(fineLocationGranted) || Boolean.TRUE.equals(coarseLocationGranted)) {
                        // Permission granted - determine type based on what user selected in system dialog
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
        
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "Notification permission granted");
                        if (getApplicationContext() != null) {
                            FCMTokenManager.getInstance().initialize(getApplicationContext());
                        }
                    } else {
                        Log.w(TAG, "Notification permission denied");
                        // Still initialize FCM - may work on older Android versions
                        if (getApplicationContext() != null) {
                            FCMTokenManager.getInstance().initialize(getApplicationContext());
                        }
                    }
                    // Capture location if permission was granted, then navigate to main app
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        captureAndSaveLocation();
                    } else {
                        // Location permission was denied, just navigate to main app
                        navigateToMainApp();
                    }
                });
        
        // Set click listener for Allow Permission button
        if (btnAllowPermission != null) {
            btnAllowPermission.setOnClickListener(v -> handleAllowPermission());
        }
    }
    
    private void handleAllowPermission() {
        // Set permission type to "while using app" as default
        selectedPermissionType = PERMISSION_WHILE_USING;
        // Request location permission first, then notification permission will be requested after location is handled
        requestLocationPermission();
    }
    
    private void requestLocationPermission() {
        // Check if permission is already granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
            navigateToMainApp();
            return;
        }
        
        setLoading(true);
        
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        saveLocationToFirestore(location);
                    } else {
                        requestNewLocation();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Failed to get location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    navigateToMainApp();
                });
    }
    
    private void requestNewLocation() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                
                if (lastKnownLocation != null) {
                    saveLocationToFirestore(lastKnownLocation);
                } else {
                    setLoading(false);
                    Toast.makeText(this, "Unable to get location. Please enable location services.", Toast.LENGTH_SHORT).show();
                    navigateToMainApp();
                }
            }
        } catch (Exception e) {
            setLoading(false);
            Toast.makeText(this, "Error getting location", Toast.LENGTH_SHORT).show();
            navigateToMainApp();
        }
    }
    
    private void saveLocationToFirestore(Location location) {
        String uid = authManager.getUid();
        if (uid == null) {
            setLoading(false);
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            navigateToMainApp();
            return;
        }
        
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
                    Toast.makeText(this, "Location saved successfully!", Toast.LENGTH_SHORT).show();
                    // Navigate to main app after location is saved
                    navigateToMainApp();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Failed to save location: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Navigate to main app even if location save failed
                    navigateToMainApp();
                });
    }
    
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting notification permission");
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                Log.d(TAG, "Notification permission already granted");
                if (getApplicationContext() != null) {
                    FCMTokenManager.getInstance().initialize(getApplicationContext());
                }
                navigateToMainApp();
            }
        } else {
            // Android 12 and below - permission granted via manifest
            Log.d(TAG, "Android version < 13, notification permission granted via manifest");
            if (getApplicationContext() != null) {
                FCMTokenManager.getInstance().initialize(getApplicationContext());
            }
            navigateToMainApp();
        }
    }
    
    private void savePermissionPreference(String permissionType) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOCATION_PERMISSION, permissionType).apply();
    }
    
    private void navigateToMainApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnAllowPermission != null) {
            btnAllowPermission.setEnabled(!loading);
        }
    }
    
    @Override
    public void onBackPressed() {
        // Prevent going back - must complete permission flow
        Toast.makeText(this, "Please complete the setup", Toast.LENGTH_SHORT).show();
    }
}

