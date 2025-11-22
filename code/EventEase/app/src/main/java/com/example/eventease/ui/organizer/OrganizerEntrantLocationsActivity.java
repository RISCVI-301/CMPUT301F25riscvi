package com.example.eventease.ui.organizer;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Activity for organizers to view waitlisted entrant locations on a map.
 * 
 * <p>This activity displays a map with markers indicating where each waitlisted entrant
 * joined the event waitlist from. Each marker shows the entrant's name.
 * 
 * <p>The activity loads location data from:
 * <ul>
 *   <li>Waitlist entry documents (if location was saved during join)</li>
 *   <li>User documents (fallback to current user location)</li>
 * </ul>
 */
public class OrganizerEntrantLocationsActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "OrganizerEntrantLocations";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private GoogleMap googleMap;
    private String currentEventId;
    
    private ImageView backButton;
    
    // List to store entrant location data
    private List<EntrantLocation> entrantLocations = new ArrayList<>();
    
    // Inner class to store entrant location data
    private static class EntrantLocation {
        String name;
        LatLng location;
        
        EntrantLocation(String name, LatLng location) {
            this.name = name;
            this.location = location;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_entrant_locations);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        currentEventId = getIntent().getStringExtra("eventId");
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Missing event ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initializing views
        backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        // Initializing map fragment
        try {
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map_fragment);
            if (mapFragment != null) {
                Log.d(TAG, "Map fragment found, requesting map");
                mapFragment.getMapAsync(this);
            } else {
                Log.e(TAG, "Map fragment is null! Check layout XML.");
                Toast.makeText(this, "Map initialization failed: fragment not found", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing map fragment", e);
            Toast.makeText(this, "Map initialization error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Loading entrant locations
        signInAndLoadData();
    }

    private void signInAndLoadData() {
        com.google.firebase.auth.FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "User is authenticated: " + currentUser.getUid());
            loadEntrantLocations();
        } else {
            Log.w(TAG, "No authenticated user, attempting anonymous sign-in");


            mAuth.signInAnonymously()
                    .addOnSuccessListener(r -> {
                        Log.d(TAG, "Anonymous sign-in successful: " + (r.getUser() != null ? r.getUser().getUid() : "null"));
                        loadEntrantLocations();
                    })
                    .addOnFailureListener(e -> {
                        String errorMsg = "Authentication failed: " + e.getMessage();
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Auth failed", e);
                    });
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        try {
            googleMap = map;
            if (googleMap == null) {
                Log.e(TAG, "Received null GoogleMap object!");
                Toast.makeText(this, "Map initialization failed: null map object", Toast.LENGTH_LONG).show();
                return;
            }
            
            googleMap.getUiSettings().setZoomControlsEnabled(true);
            googleMap.getUiSettings().setCompassEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            
            Log.d(TAG, "Map is ready. Current entrant locations count: " + entrantLocations.size());
            

            LatLng defaultLocation = new LatLng(53.5461, -113.4938);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 2f));
            Log.d(TAG, "Set default camera position to Edmonton");
            

            if (!entrantLocations.isEmpty()) {
                Log.d(TAG, "Updating map markers with existing locations");
                updateMapMarkers();
            } else {
                Log.d(TAG, "No locations yet, waiting for data to load");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onMapReady", e);
            Toast.makeText(this, "Map error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadEntrantLocations() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Log.e(TAG, "Event ID is null or empty");
            return;
        }

        Log.d(TAG, "Loading entrant locations for event: " + currentEventId);
        entrantLocations.clear();

        //getting waitlisted IDs from event document
        db.collection("events").document(currentEventId).get()
                .addOnSuccessListener(eventDoc -> {
                    if (!eventDoc.exists()) {
                        Log.e(TAG, "Event document does not exist: " + currentEventId);
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<String> waitlistedIds = (List<String>) eventDoc.get("waitlistedEntrants");
                    if (waitlistedIds != null && !waitlistedIds.isEmpty()) {
                        Log.d(TAG, "Found " + waitlistedIds.size() + " waitlisted IDs in event document");
                        // Loading locations for each waitlisted entrant
                        for (String userId : waitlistedIds) {
                            fetchEntrantLocation(currentEventId, userId);
                        }
                    } else {
                        Log.d(TAG, "No waitlistedEntrants array in event doc, reading subcollection directly");

                        db.collection("events").document(currentEventId)
                                .collection("WaitlistedEntrants").get()
                                .addOnSuccessListener(snap -> {
                                    if (snap.isEmpty()) {
                                        Log.w(TAG, "No waitlisted entrants found in subcollection");
                                        Toast.makeText(this, "No waitlisted entrants found", 
                                                Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    
                                    Log.d(TAG, "Found " + snap.size() + " waitlisted entrants in subcollection");
                                    for (DocumentSnapshot doc : snap.getDocuments()) {
                                        String userId = doc.getId();
                                        Log.d(TAG, "Processing entrant with userId: " + userId);
                                        fetchEntrantLocation(currentEventId, userId);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to read WaitlistedEntrants subcollection", e);
                                    String errorMsg = "Failed to load entrants";
                                    if (e.getMessage() != null) {
                                        if (e.getMessage().contains("PERMISSION_DENIED")) {
                                            errorMsg = "Permission denied: Check Firestore security rules";
                                            Log.e(TAG, "Firestore security rules are blocking access to waitlist data");
                                        } else if (e.getMessage().contains("UNAVAILABLE") || e.getMessage().contains("Unable to resolve host")) {
                                            errorMsg = "Network error: Check your internet connection";
                                            Log.e(TAG, "Network connectivity issue: " + e.getMessage());
                                        }
                                    }
                                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Event load failed", e);
                    String errorMsg = "Failed to load event";
                    if (e.getMessage() != null) {
                        if (e.getMessage().contains("PERMISSION_DENIED")) {
                            errorMsg = "Permission denied: Check Firestore security rules";
                            Log.e(TAG, "Firestore security rules are blocking access to event data");
                        } else if (e.getMessage().contains("UNAVAILABLE") || e.getMessage().contains("Unable to resolve host")) {
                            errorMsg = "Network error: Check your internet connection";
                            Log.e(TAG, "Network connectivity issue: " + e.getMessage());
                        }
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                });
    }

    private void fetchEntrantLocation(String eventId, String userId) {
        Log.d(TAG, "Fetching location for userId: " + userId);
        

        db.collection("events").document(eventId)
                .collection("WaitlistedEntrants").document(userId)
                .get()
                .addOnSuccessListener(waitlistDoc -> {
                    // Extracting display name and making it final for using in nested lambda
                    String extractedName = extractNameFromMap(waitlistDoc.getData());
                    final String displayName = (extractedName == null || extractedName.trim().isEmpty()) 
                            ? "Entrant (" + userId.substring(0, Math.min(6, userId.length())) + ")"
                            : extractedName;

                    Log.d(TAG, "Waitlist doc exists: " + waitlistDoc.exists() + ", Display name: " + displayName);

                    if (waitlistDoc.exists()) {
                        // Checking if location exists in waitlist entry
                        Object locationObj = waitlistDoc.get("location");
                        Log.d(TAG, "Location object from waitlist: " + (locationObj != null ? locationObj.getClass().getName() : "null"));
                        
                        if (locationObj != null) {
                            LatLng location = extractLocation(locationObj);
                            
                            if (location != null) {
                                Log.d(TAG, "Found location in waitlist entry: " + location.latitude + ", " + location.longitude);
                                addEntrantLocation(displayName, location);
                                return;
                            } else {
                                Log.d(TAG, "Failed to extract location from waitlist entry object");
                            }
                        } else {
                            Log.d(TAG, "No location object in waitlist entry");
                        }
                    }
                    

                    Log.d(TAG, "Fetching location from user document for: " + userId);
                    db.collection("users").document(userId).get()
                            .addOnSuccessListener(userDoc -> {
                                if (userDoc.exists()) {

                                    Object locationObj = userDoc.get("location");
                                    Log.d(TAG, "Location object from user doc: " + (locationObj != null ? locationObj.getClass().getName() : "null"));
                                    
                                    LatLng location = null;
                                    
                                    if (locationObj != null) {
                                        Log.d(TAG, "Location object type: " + locationObj.getClass().getName());
                                        
                                        // Logging the raw location data
                                        if (locationObj instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> locationMap = (Map<String, Object>) locationObj;
                                            Log.d(TAG, "Location map keys: " + locationMap.keySet());
                                            Log.d(TAG, "Location map: " + locationMap);
                                        }
                                        
                                        location = extractLocation(locationObj);
                                    }
                                    
                                    // If location not found in nested object, trying root-level latitude/longitude
                                    if (location == null) {
                                        Log.d(TAG, "Location not in nested object, trying root-level latitude/longitude");
                                        Double lat = userDoc.getDouble("latitude");
                                        Double lng = userDoc.getDouble("longitude");
                                        
                                        if (lat != null && lng != null && isValidLatLng(lat, lng)) {
                                            Log.d(TAG, "Found root-level coordinates: lat=" + lat + ", lng=" + lng);
                                            location = new LatLng(lat, lng);
                                        } else {
                                            Log.w(TAG, "Invalid root-level coordinates: lat=" + lat + ", lng=" + lng);
                                        }
                                    }
                                    
                                    if (location != null) {
                                        Log.d(TAG, "Found location in user document: " + location.latitude + ", " + location.longitude);
                                        addEntrantLocation(displayName, location);
                                    } else {
                                        Log.w(TAG, "No location data found in user document for: " + userId);
                                    }
                                } else {
                                    Log.w(TAG, "User document does not exist for: " + userId);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Failed to fetch location for user " + userId, e);
                                // Check for permission denied errors specifically
                                if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
                                    Log.e(TAG, "Permission denied: Firestore security rules may be blocking access to user location data");
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to fetch waitlist entry for user " + userId, e);

                    if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
                        Log.e(TAG, "Permission denied: Firestore security rules may be blocking access to waitlist data");
                    }
                });
    }

    private LatLng extractLocation(Object locationObj) {
        if (locationObj == null) {
            Log.d(TAG, "Location object is null");
            return null;
        }
        
        Log.d(TAG, "Extracting location from type: " + locationObj.getClass().getName());
        
        if (locationObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> locationMap = (Map<String, Object>) locationObj;
            Log.d(TAG, "Location map contents: " + locationMap);
            
            Double lat = getDoubleFromMap(locationMap, "latitude");
            Double lng = getDoubleFromMap(locationMap, "longitude");
            
            Log.d(TAG, "Extracted lat: " + lat + ", lng: " + lng);
            
            if (lat != null && lng != null) {
                // Validating coordinates are within valid ranges
                if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                    return new LatLng(lat, lng);
                } else {
                    Log.w(TAG, "Invalid coordinates: lat=" + lat + ", lng=" + lng);
                    return null;
                }
            } else {
                Log.w(TAG, "Could not extract lat/lng from map. lat=" + lat + ", lng=" + lng);
                return null;
            }
        } else if (locationObj instanceof GeoPoint) {
            GeoPoint geoPoint = (GeoPoint) locationObj;
            Log.d(TAG, "Extracted GeoPoint: lat=" + geoPoint.getLatitude() + ", lng=" + geoPoint.getLongitude());
            return new LatLng(geoPoint.getLatitude(), geoPoint.getLongitude());
        } else {
            Log.w(TAG, "Unknown location object type: " + locationObj.getClass().getName());
            return null;
        }
    }

    private Double getDoubleFromMap(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object value = map.get(key);
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private boolean isValidLatLng(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    private String extractNameFromMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return null;
        
        Object name = data.get("name");
        if (name instanceof String && !((String) name).trim().isEmpty()) {
            return (String) name;
        }
        
        Object full = data.get("fullName");
        if (full instanceof String && !((String) full).trim().isEmpty()) {
            return (String) full;
        }
        
        Object displayName = data.get("displayName");
        if (displayName instanceof String && !((String) displayName).trim().isEmpty()) {
            return (String) displayName;
        }
        
        String first = data.get("firstName") instanceof String ? (String) data.get("firstName") : null;
        String last = data.get("lastName") instanceof String ? (String) data.get("lastName") : null;
        if ((first != null && !first.isEmpty()) || (last != null && !last.isEmpty())) {
            return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        }
        
        return null;
    }

    private void addEntrantLocation(String name, LatLng location) {
        Log.d(TAG, "Adding entrant location: " + name + " at (" + location.latitude + ", " + location.longitude + ")");
        entrantLocations.add(new EntrantLocation(name, location));
        Log.d(TAG, "Total entrant locations now: " + entrantLocations.size());
        

        if (googleMap != null) {
            Log.d(TAG, "Map is ready, updating markers");
            updateMapMarkers();
        } else {
            Log.d(TAG, "Map not ready yet, markers will be added when map becomes ready");
        }
    }

    private void updateMapMarkers() {
        Log.d(TAG, "updateMapMarkers called. Map ready: " + (googleMap != null) + ", Locations count: " + entrantLocations.size());
        
        if (googleMap == null) {
            Log.w(TAG, "GoogleMap is null, cannot update markers");
            return;
        }
        
        if (entrantLocations.isEmpty()) {
            Log.w(TAG, "No entrant locations to display");
            Toast.makeText(this, "No location data available for waitlisted entrants", 
                    Toast.LENGTH_SHORT).show();
            return;
        }

        googleMap.clear();
        Log.d(TAG, "Cleared existing markers");

        // Adding markers for all entrants with valid locations
        com.google.android.gms.maps.model.LatLngBounds.Builder boundsBuilder = 
                new com.google.android.gms.maps.model.LatLngBounds.Builder();
        boolean hasValidLocation = false;
        int markerCount = 0;

        for (EntrantLocation entrant : entrantLocations) {
            if (entrant.location != null) {
                Log.d(TAG, "Adding marker for " + entrant.name + " at (" + 
                        entrant.location.latitude + ", " + entrant.location.longitude + ")");
                googleMap.addMarker(new MarkerOptions()
                        .position(entrant.location));
                boundsBuilder.include(entrant.location);
                hasValidLocation = true;
                markerCount++;
            } else {
                Log.w(TAG, "Entrant " + entrant.name + " has null location");
            }
        }

        Log.d(TAG, "Added " + markerCount + " markers to map");

        // Zooming to show all markers
        if (hasValidLocation) {
            try {
                com.google.android.gms.maps.model.LatLngBounds bounds = boundsBuilder.build();
                Log.d(TAG, "Zooming to bounds: " + bounds);
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            } catch (IllegalStateException e) {
                Log.w(TAG, "IllegalStateException when setting bounds, using fallback", e);
                // Fallback if bounds not ready yet - use first location as center
                if (!entrantLocations.isEmpty() && entrantLocations.get(0).location != null) {
                    LatLng firstLocation = entrantLocations.get(0).location;
                    Log.d(TAG, "Using fallback: centering on first location at (" + 
                            firstLocation.latitude + ", " + firstLocation.longitude + ")");
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstLocation, 10f));
                }
            }
        } else {
            // If no locations found, showing a message
            Log.w(TAG, "No valid locations found to display");
            Toast.makeText(this, "No location data available for waitlisted entrants", 
                    Toast.LENGTH_SHORT).show();
        }
    }
}

