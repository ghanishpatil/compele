package com.example.mystartup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mystartup.fragments.UserAttendanceFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import android.widget.Toast;

public class UserAttendanceActivity extends AppCompatActivity {
    private static final String TAG = "UserAttendanceActivity";
    private static final String PREF_NAME = "AuthPrefs";
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_attendance);
        
        db = FirebaseFirestore.getInstance();
        
        // Get location details from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String locationId = prefs.getString("selected_location_id", "");
        String locationName = prefs.getString("selected_location_name", "");
        float latitude = prefs.getFloat("selected_location_latitude", 0f);
        float longitude = prefs.getFloat("selected_location_longitude", 0f);
        int radius = prefs.getInt("selected_location_radius", 100);
        
        // Log the location details being passed to the fragment
        Log.d(TAG, "Loading location from SharedPreferences:");
        Log.d(TAG, "  Location ID: " + locationId);
        Log.d(TAG, "  Location Name: " + locationName);
        Log.d(TAG, "  Latitude: " + latitude);
        Log.d(TAG, "  Longitude: " + longitude);
        Log.d(TAG, "  Radius: " + radius + "m");
        
        // If location data is incomplete, try to fetch it from Firestore
        if (locationId != null && !locationId.isEmpty() && (latitude == 0f || longitude == 0f)) {
            Log.w(TAG, "Incomplete location data, fetching from Firestore");
            fetchLocationDataFromFirestore(locationId, locationName, radius);
            return; // Exit early, we'll create the fragment once data is loaded
        } else if (locationId == null || locationId.isEmpty()) {
            // No location ID, try to get user's assigned location from Firestore
            Log.w(TAG, "No location ID, trying to get user's location from Firestore");
            fetchUserLocationFromFirestore();
            return; // Exit early, we'll create the fragment once data is loaded
        } else {
            // Location data seems valid, proceed with creating the fragment
            createAttendanceFragmentWithLocation(locationId, locationName, latitude, longitude, radius);
        }
    }
    
    private void fetchLocationDataFromFirestore(String locationId, String locationName, int radius) {
        db.collection("office_locations")
            .document(locationId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Get location coordinates
                    Double latitude = documentSnapshot.getDouble("latitude");
                    Double longitude = documentSnapshot.getDouble("longitude");
                    Long radiusLong = documentSnapshot.getLong("radius");
                    
                    // Use default radius if not specified
                    int updatedRadius = (radiusLong != null) ? radiusLong.intValue() : radius;
                    
                    // Save the coordinates to SharedPreferences for future use
                    SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    
                    if (latitude != null && longitude != null) {
                        editor.putFloat("selected_location_latitude", latitude.floatValue());
                        editor.putFloat("selected_location_longitude", longitude.floatValue());
                        editor.putInt("selected_location_radius", updatedRadius);
                        editor.apply();
                        
                        Log.d(TAG, "Successfully fetched location coordinates from Firestore: " +
                              "lat=" + latitude + ", long=" + longitude + ", radius=" + updatedRadius + "m");
                        
                        // Create fragment with fetched location data
                        createAttendanceFragmentWithLocation(locationId, locationName, 
                                                            latitude.floatValue(), longitude.floatValue(), 
                                                            updatedRadius);
                    } else {
                        Log.e(TAG, "Invalid location coordinates in Firestore");
                        createAttendanceFragmentWithLocation(locationId, locationName, 0f, 0f, radius);
                    }
                } else {
                    Log.e(TAG, "Location document not found in Firestore");
                    createAttendanceFragmentWithLocation(locationId, locationName, 0f, 0f, radius);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching location from Firestore: " + e.getMessage());
                createAttendanceFragmentWithLocation(locationId, locationName, 0f, 0f, radius);
            });
    }
    
    private void fetchUserLocationFromFirestore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "User not logged in. Redirecting to login");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        
        // Try to get user by UID first
        db.collection("users")
            .whereEqualTo("uid", user.getUid())
            .limit(1)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    // Get the first document
                    com.google.firebase.firestore.DocumentSnapshot userDoc = queryDocumentSnapshots.getDocuments().get(0);
                    processUserDocument(userDoc);
                } else {
                    // Try to find by email/sevarthId
                    String email = user.getEmail();
                    if (email != null && email.contains("@")) {
                        String sevarthId = email.substring(0, email.indexOf('@'));
                        
                        // Query by sevarthId
                        db.collection("users")
                            .whereEqualTo("sevarthId", sevarthId)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(snapshots -> {
                                if (!snapshots.isEmpty()) {
                                    processUserDocument(snapshots.getDocuments().get(0));
                                } else {
                                    Log.e(TAG, "User document not found in Firestore");
                                    createAttendanceFragmentWithLocation("", "", 0f, 0f, 100);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error querying user by sevarthId: " + e.getMessage());
                                createAttendanceFragmentWithLocation("", "", 0f, 0f, 100);
                            });
                    } else {
                        Log.e(TAG, "User has no email");
                        createAttendanceFragmentWithLocation("", "", 0f, 0f, 100);
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error querying user by UID: " + e.getMessage());
                createAttendanceFragmentWithLocation("", "", 0f, 0f, 100);
            });
    }
    
    private void processUserDocument(com.google.firebase.firestore.DocumentSnapshot userDoc) {
        // Get location IDs from user document
        Object locationIdsObj = userDoc.get("locationIds");
        if (locationIdsObj instanceof java.util.List) {
            java.util.List<String> locationIds = (java.util.List<String>) locationIdsObj;
            if (!locationIds.isEmpty()) {
                String locationId = locationIds.get(0);
                
                // Get location name if available
                String locationName = "";
                Object locationNamesObj = userDoc.get("locationNames");
                if (locationNamesObj instanceof java.util.List) {
                    java.util.List<String> locationNames = (java.util.List<String>) locationNamesObj;
                    if (!locationNames.isEmpty()) {
                        locationName = locationNames.get(0);
                    }
                }
                
                // Save to SharedPreferences
                SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("selected_location_id", locationId);
                editor.putString("selected_location_name", locationName);
                editor.apply();
                
                // Fetch the actual location coordinates
                fetchLocationDataFromFirestore(locationId, locationName, 100);
            } else {
                Log.e(TAG, "User has no assigned locations");
                Toast.makeText(this, "No locations assigned to your profile", Toast.LENGTH_LONG).show();
                createAttendanceFragmentWithLocation("", "", 0f, 0f, 100);
            }
        } else {
            Log.e(TAG, "User has no locationIds field or it's not a list");
            createAttendanceFragmentWithLocation("", "", 0f, 0f, 100);
        }
    }
    
    private void createAttendanceFragmentWithLocation(String locationId, String locationName, 
                                                    float latitude, float longitude, int radius) {
        // Create fragment with location data
        UserAttendanceFragment fragment = new UserAttendanceFragment();
        
        // Pass location data to fragment
        Bundle args = new Bundle();
        args.putString("location_id", locationId);
        args.putString("location_name", locationName);
        args.putFloat("location_latitude", latitude);
        args.putFloat("location_longitude", longitude);
        args.putInt("location_radius", radius);
        fragment.setArguments(args);
        
        // Add fragment to container
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    @Override
    public void onBackPressed() {
        // Navigate back to LocationSelectionActivity instead of closing the app
        Intent intent = new Intent(this, LocationSelectionActivity.class);
        startActivity(intent);
        finish();
    }
} 