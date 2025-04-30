package com.example.mystartup;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.mystartup.databinding.ActivityAddOfficeLocationBinding;
import com.example.mystartup.models.OfficeLocation;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AddOfficeLocationActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    private ActivityAddOfficeLocationBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String editId = null; // Will be set if we're editing an existing location

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddOfficeLocationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check for location permissions
        checkLocationPermissions();

        // Check if we're editing an existing location
        if (getIntent().hasExtra("LOCATION_ID")) {
            editId = getIntent().getStringExtra("LOCATION_ID");
            setupForEditing(editId);
            
            // Change title and button text for editing
            binding.topBarTitle.setText("Edit Work Location");
            binding.addButton.setText("Update");
        }

        setupTalukaDropdown();
        setupClickListeners();
        
        // Default radius value
        if (binding.radiusEditText.getText().toString().isEmpty()) {
            binding.radiusEditText.setText("100");
        }
    }
    
    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied
                Toast.makeText(this, "Location permission required for this feature", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupTalukaDropdown() {
        // Populate Taluka dropdown with only Hingoli and Sengaon
        List<String> talukaOptions = Arrays.asList("Hingoli", "Sengaon");
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, talukaOptions);
        
        AutoCompleteTextView talukaDropdown = binding.talukaDropdown;
        talukaDropdown.setAdapter(adapter);
        
        // Set default selection to Hingoli if not editing
        if (editId == null && TextUtils.isEmpty(talukaDropdown.getText())) {
            talukaDropdown.setText("Hingoli", false);
        }
    }

    private void setupForEditing(String locationId) {
        // Load existing location data for editing
        db.collection("office_locations").document(locationId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        OfficeLocation location = documentSnapshot.toObject(OfficeLocation.class);
                        if (location != null) {
                            binding.officeNameEditText.setText(location.getName());
                            binding.talukaDropdown.setText(location.getTaluka());
                            binding.latitudeEditText.setText(String.valueOf(location.getLatitude()));
                            binding.longitudeEditText.setText(String.valueOf(location.getLongitude()));
                            binding.radiusEditText.setText(String.valueOf(location.getRadius()));
                        }
                    }
                })
                .addOnFailureListener(e -> 
                    Toast.makeText(this, "Failed to load location data", Toast.LENGTH_SHORT).show());
    }

    private void setupClickListeners() {
        // Back button
        binding.backButton.setOnClickListener(v -> finish());
        
        // Cancel button
        binding.cancelButton.setOnClickListener(v -> finish());
        
        // Add/Update button
        binding.addButton.setOnClickListener(v -> {
            if (validateInputs()) {
                saveLocationToFirebase();
            }
        });
    }

    private boolean validateInputs() {
        boolean isValid = true;
        
        // Validate Office Name
        String officeName = binding.officeNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(officeName)) {
            binding.officeNameLayout.setError("Office name is required");
            isValid = false;
        } else {
            binding.officeNameLayout.setError(null);
        }
        
        // Validate Latitude
        String latitudeStr = binding.latitudeEditText.getText().toString().trim();
        if (TextUtils.isEmpty(latitudeStr)) {
            binding.latitudeLayout.setError("Latitude is required");
            isValid = false;
        } else {
            try {
                double latitude = Double.parseDouble(latitudeStr);
                if (latitude < -90 || latitude > 90) {
                    binding.latitudeLayout.setError("Latitude must be between -90 and 90");
                    isValid = false;
                } else {
                    binding.latitudeLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                binding.latitudeLayout.setError("Invalid latitude format");
                isValid = false;
            }
        }
        
        // Validate Longitude
        String longitudeStr = binding.longitudeEditText.getText().toString().trim();
        if (TextUtils.isEmpty(longitudeStr)) {
            binding.longitudeLayout.setError("Longitude is required");
            isValid = false;
        } else {
            try {
                double longitude = Double.parseDouble(longitudeStr);
                if (longitude < -180 || longitude > 180) {
                    binding.longitudeLayout.setError("Longitude must be between -180 and 180");
                    isValid = false;
                } else {
                    binding.longitudeLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                binding.longitudeLayout.setError("Invalid longitude format");
                isValid = false;
            }
        }
        
        // Validate Radius
        String radiusStr = binding.radiusEditText.getText().toString().trim();
        if (TextUtils.isEmpty(radiusStr)) {
            binding.radiusEditText.setText("100"); // Default value
        } else {
            try {
                int radius = Integer.parseInt(radiusStr);
                if (radius <= 0) {
                    binding.radiusLayout.setError("Radius must be greater than 0");
                    isValid = false;
                } else {
                    binding.radiusLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                binding.radiusLayout.setError("Invalid radius format");
                isValid = false;
            }
        }
        
        return isValid;
    }

    private void saveLocationToFirebase() {
        String officeName = binding.officeNameEditText.getText().toString().trim();
        String taluka = binding.talukaDropdown.getText().toString().trim();
        double latitude = Double.parseDouble(binding.latitudeEditText.getText().toString().trim());
        double longitude = Double.parseDouble(binding.longitudeEditText.getText().toString().trim());
        int radius = Integer.parseInt(binding.radiusEditText.getText().toString().trim());
        
        // Check if user is authenticated
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "You must be signed in to perform this action", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String userId = mAuth.getCurrentUser().getUid();
        String locationId = editId != null ? editId : UUID.randomUUID().toString();
        
        OfficeLocation officeLocation = new OfficeLocation(
                locationId,
                officeName,
                taluka,
                latitude,
                longitude,
                radius,
                userId
        );
        
        // Show progress indicator
        binding.addButton.setEnabled(false);
        binding.cancelButton.setEnabled(false);
        Toast.makeText(this, "Saving location...", Toast.LENGTH_SHORT).show();
        
        db.collection("office_locations").document(locationId)
                .set(officeLocation)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, 
                        editId != null ? "Location updated successfully" : "Location added successfully", 
                        Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    // Re-enable buttons
                    binding.addButton.setEnabled(true);
                    binding.cancelButton.setEnabled(true);
                    
                    // Show detailed error message
                    String errorMsg = "Failed to save location: " + e.getMessage();
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    
                    // Log the error
                    Log.e("AddOfficeLocation", errorMsg, e);
                });
    }
} 