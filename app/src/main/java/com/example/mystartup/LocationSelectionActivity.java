package com.example.mystartup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import android.graphics.Rect;

import com.example.mystartup.adapters.LocationSelectionAdapter;
import com.example.mystartup.api.RetrofitClient;
import com.example.mystartup.databinding.ActivityLocationSelectionBinding;
import com.example.mystartup.models.OfficeLocation;
import com.example.mystartup.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class LocationSelectionActivity extends AppCompatActivity implements LocationSelectionAdapter.OnLocationSelectedListener {
    private ActivityLocationSelectionBinding binding;
    private LocationSelectionAdapter adapter;
    private List<OfficeLocation> locations = new ArrayList<>();
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private static final String PREF_NAME = "AuthPrefs";
    private static final String KEY_SEVARTH_ID = "sevarth_id";
    private static final String KEY_SELECTED_LOCATION_ID = "selected_location_id";
    private static final String KEY_SELECTED_LOCATION_NAME = "selected_location_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocationSelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupRecyclerView();
        setupLogoutButton();
        loadUserLocations();
    }

    private void setupRecyclerView() {
        adapter = new LocationSelectionAdapter(locations, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.locationsRecyclerView.setLayoutManager(layoutManager);
        binding.locationsRecyclerView.setAdapter(adapter);
        
        // Add better visuals
        binding.locationsRecyclerView.setHasFixedSize(true);
        binding.locationsRecyclerView.setNestedScrollingEnabled(false);
        
        // Add bottom space to last item
        int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.recycler_item_spacing);
        binding.locationsRecyclerView.addItemDecoration(new ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, 
                                      @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                // Add space to bottom of each item
                outRect.bottom = spacingInPixels;
            }
        });
    }

    private void setupLogoutButton() {
        binding.logoutButton.setOnClickListener(v -> {
            // Clear saved preferences
            getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

            // Sign out from Firebase
            mAuth.signOut();

            // Return to login screen
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void loadUserLocations() {
        showLoading(true);

        // Get the current user's Sevarth ID
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String sevarthId = prefs.getString(KEY_SEVARTH_ID, "");

        if (sevarthId.isEmpty()) {
            // If Sevarth ID is not found, try to get it from the user document using UID
            String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
            if (uid != null) {
                db.collection("users")
                    .whereEqualTo("uid", uid)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            User user = queryDocumentSnapshots.getDocuments().get(0).toObject(User.class);
                            if (user != null) {
                                // Save the Sevarth ID for future use
                                prefs.edit().putString(KEY_SEVARTH_ID, user.getSevarthId()).apply();
                                // Load locations using the retrieved Sevarth ID
                                fetchUserLocations(user.getSevarthId());
                            } else {
                                showError("Failed to load user data");
                            }
                        } else {
                            showError("User not found");
                        }
                    })
                    .addOnFailureListener(e -> {
                        showError("Error fetching user data: " + e.getMessage());
                    });
            } else {
                showError("User authentication required");
                navigateToLogin();
            }
        } else {
            fetchUserLocations(sevarthId);
        }
    }

    private void fetchUserLocations(String sevarthId) {
        // First, get the user document to retrieve assigned location IDs
        db.collection("users")
            .whereEqualTo("sevarthId", sevarthId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    QueryDocumentSnapshot userDoc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                    User user = userDoc.toObject(User.class);
                    
                    if (user != null && user.getLocationIds() != null && !user.getLocationIds().isEmpty()) {
                        // User has assigned locations, fetch each location's details
                        fetchLocationDetails(user.getLocationIds());
                    } else {
                        // User has no assigned locations
                        showLoading(false);
                        showEmptyView(true);
                    }
                } else {
                    showError("User not found");
                    showLoading(false);
                }
            })
            .addOnFailureListener(e -> {
                showError("Error fetching user data: " + e.getMessage());
                showLoading(false);
            });
    }

    private void fetchLocationDetails(List<String> locationIds) {
        // Counter to track fetched locations
        final int[] count = {0};
        
        // Clear previous locations
        locations.clear();
        
        // Fetch each location by ID
        for (String locationId : locationIds) {
            db.collection("office_locations")
                .document(locationId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    count[0]++;
                    
                    if (documentSnapshot.exists()) {
                        OfficeLocation location = documentSnapshot.toObject(OfficeLocation.class);
                        if (location != null) {
                            locations.add(location);
                            adapter.notifyItemInserted(locations.size() - 1);
                        }
                    }
                    
                    // Check if all locations have been fetched
                    if (count[0] == locationIds.size()) {
                        showLoading(false);
                        if (locations.isEmpty()) {
                            showEmptyView(true);
                        } else {
                            showEmptyView(false);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    count[0]++;
                    
                    // Check if all locations have been attempted
                    if (count[0] == locationIds.size()) {
                        showLoading(false);
                        if (locations.isEmpty()) {
                            showEmptyView(true);
                        } else {
                            showEmptyView(false);
                        }
                    }
                    
                    showError("Error fetching location data: " + e.getMessage());
                });
        }
    }

    private void showEmptyView(boolean show) {
        binding.locationsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onLocationSelected(OfficeLocation location) {
        // Save the selected location ID, name, and coordinates
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_SELECTED_LOCATION_ID, location.getId())
            .putString(KEY_SELECTED_LOCATION_NAME, location.getName())
            .putFloat("selected_location_latitude", location.getLatitude().floatValue())
            .putFloat("selected_location_longitude", location.getLongitude().floatValue())
            .putInt("selected_location_radius", location.getRadius() != null ? location.getRadius() : 100)
            .apply();

        // Navigate to the UserAttendanceActivity
        Intent intent = new Intent(this, UserAttendanceActivity.class);
        startActivity(intent);
        finish();
    }
} 