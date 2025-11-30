package com.example.eventease.ui.organizer;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.eventease.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity that displays a Google Map showing the locations of waitlisted entrants for an event.
 * Each entrant's location is displayed as a marker on the map with their name.
 */
public class OrganizerEntrantLocationsActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "EntrantLocations";
    
    private GoogleMap mMap;
    private FirebaseFirestore db;
    private String eventId;
    private TextView titleTextView;
    private ImageView backButton;
    
    // Store entrant data for marker info windows
    private Map<String, EntrantInfo> entrantInfoMap = new HashMap<>();
    
    /**
     * Data class to hold entrant information
     */
    private static class EntrantInfo {
        String name;
        String userId;
        double latitude;
        double longitude;
        
        EntrantInfo(String name, String userId, double latitude, double longitude) {
            this.name = name;
            this.userId = userId;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        setContentView(R.layout.activity_organizer_entrant_locations);
        
        db = FirebaseFirestore.getInstance();
        eventId = getIntent().getStringExtra("eventId");
        
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize views
        titleTextView = findViewById(R.id.map_title);
        backButton = findViewById(R.id.back_button);
        
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        
        // Load event title and waitlisted entrants
        loadEventData();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        
        // Configure map settings
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        
        // Set custom info window adapter to show entrant names
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public android.view.View getInfoWindow(Marker marker) {
                return null; // Use default info window
            }
            
            @Override
            public android.view.View getInfoContents(Marker marker) {
                return null; // Use default info window
            }
        });
        
        // Load and display entrant locations
        loadWaitlistedEntrants();
    }
    
    private void loadEventData() {
        // Title is already set to "Entrants Locations" in XML, no need to change it
        // This method is kept for potential future use if needed
    }
    
    private void loadWaitlistedEntrants() {
        if (eventId == null || eventId.isEmpty() || mMap == null) return;
        
        // Clear existing markers
        mMap.clear();
        entrantInfoMap.clear();
        
        // Get all waitlisted entrants from the subcollection
        db.collection("events").document(eventId).collection("WaitlistedEntrants")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        Toast.makeText(this, "No waitlisted entrants found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    List<String> userIds = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String userId = doc.getId();
                        userIds.add(userId);
                    }
                    
                    // Load user locations for all waitlisted entrants
                    loadUserLocations(userIds);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load waitlisted entrants", e);
                    Toast.makeText(this, "Failed to load entrants: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    private void loadUserLocations(List<String> userIds) {
        if (userIds.isEmpty() || mMap == null) return;
        
        final List<LatLng> allLocations = new ArrayList<>();
        final int[] loadedCount = {0}; // Use array to allow modification in lambda
        final int totalCount = userIds.size();
        
        // Load each user's location data
        for (String userId : userIds) {
            db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(userDoc -> {
                        if (userDoc.exists()) {
                            // Extract name
                            String name = extractNameFromUserDoc(userDoc);
                            if (name == null || name.isEmpty()) {
                                name = "Entrant (" + userId.substring(0, Math.min(6, userId.length())) + ")";
                            }
                            
                            // Extract location
                            Map<String, Object> locationData = (Map<String, Object>) userDoc.get("location");
                            if (locationData != null) {
                                Object latObj = locationData.get("latitude");
                                Object lngObj = locationData.get("longitude");
                                
                                if (latObj instanceof Number && lngObj instanceof Number) {
                                    double latitude = ((Number) latObj).doubleValue();
                                    double longitude = ((Number) lngObj).doubleValue();
                                    
                                    // Create marker
                                    LatLng location = new LatLng(latitude, longitude);
                                    MarkerOptions markerOptions = new MarkerOptions()
                                            .position(location)
                                            .title(name)
                                            .snippet("Waitlisted Entrant");
                                    
                                    Marker marker = mMap.addMarker(markerOptions);
                                    
                                    // Store entrant info
                                    entrantInfoMap.put(marker.getId(), new EntrantInfo(name, userId, latitude, longitude));
                                    allLocations.add(location);
                                }
                            }
                        }
                        
                        // Increment counter and check if all are loaded
                        loadedCount[0]++;
                        if (loadedCount[0] >= totalCount) {
                            // All users processed, fit map to markers
                            if (!allLocations.isEmpty()) {
                                fitMapToMarkers(allLocations);
                            } else {
                                Toast.makeText(this, "No entrant locations available", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to load user location for " + userId, e);
                        // Increment counter even on failure
                        loadedCount[0]++;
                        if (loadedCount[0] >= totalCount) {
                            // All users processed, fit map to markers
                            if (!allLocations.isEmpty()) {
                                fitMapToMarkers(allLocations);
                            } else {
                                Toast.makeText(this, "No entrant locations available", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }
    
    private void fitMapToMarkers(List<LatLng> locations) {
        if (locations.isEmpty() || mMap == null) return;
        
        if (locations.size() == 1) {
            // Single marker - center on it with default zoom
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locations.get(0), 12f));
        } else {
            // Multiple markers - calculate bounds
            com.google.android.gms.maps.model.LatLngBounds.Builder builder = 
                    new com.google.android.gms.maps.model.LatLngBounds.Builder();
            for (LatLng location : locations) {
                builder.include(location);
            }
            com.google.android.gms.maps.model.LatLngBounds bounds = builder.build();
            
            // Add padding and animate camera
            int padding = 100; // padding in pixels
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
        }
    }
    
    private String extractNameFromUserDoc(DocumentSnapshot userDoc) {
        if (userDoc == null) return null;
        
        String name = userDoc.getString("name");
        if (name != null && !name.trim().isEmpty()) return name;
        
        String fullName = userDoc.getString("fullName");
        if (fullName != null && !fullName.trim().isEmpty()) return fullName;
        
        String firstName = userDoc.getString("firstName");
        String lastName = userDoc.getString("lastName");
        if ((firstName != null && !firstName.isEmpty()) || (lastName != null && !lastName.isEmpty())) {
            return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        }
        
        return null;
    }
}

