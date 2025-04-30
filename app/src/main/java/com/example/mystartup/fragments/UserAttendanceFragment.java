package com.example.mystartup.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.FragmentTransaction;

import com.example.mystartup.R;
import com.example.mystartup.api.AttendanceResponse;
import com.example.mystartup.api.FaceVerificationResponse;
import com.example.mystartup.databinding.FragmentUserAttendanceBinding;
import com.example.mystartup.utils.FaceRecognitionRepository;
import com.example.mystartup.utils.FaceRecognitionSystem;
import com.example.mystartup.LoginActivity;
import com.example.mystartup.AttendanceHistoryActivity;
import com.example.mystartup.utils.FirestoreAttendanceRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.widget.Button;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.Intent;

public class UserAttendanceFragment extends Fragment implements FaceRecognitionSystem.VerificationListener, LocationListener {
    private static final String TAG = "UserAttendanceFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int LOCATION_UPDATE_INTERVAL = 10000; // 10 seconds
    private static final int LOCATION_UPDATE_DISTANCE = 5; // 5 meters

    private FragmentUserAttendanceBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userName;
    private String userId;
    private String sevarthId;
    private FaceRecognitionSystem faceRecognitionSystem;
    private boolean isCameraInitialized = false;
    private FaceRecognitionRepository faceRepository;
    private FirestoreAttendanceRepository firestoreAttendanceRepository;
    
    // Location related fields
    private LocationManager locationManager;
    private String locationId;
    private String locationName;
    private double officeLatitude;
    private double officeLongitude;
    private int officeRadius; // in meters
    private boolean isWithinOfficeRadius = false;
    private Snackbar locationStatusSnackbar;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        faceRepository = new FaceRecognitionRepository();
        firestoreAttendanceRepository = new FirestoreAttendanceRepository();
        
        // Get location details from arguments
        Bundle args = getArguments();
        if (args != null) {
            locationId = args.getString("location_id", "");
            locationName = args.getString("location_name", "");
            officeLatitude = args.getFloat("location_latitude", 0f);
            officeLongitude = args.getFloat("location_longitude", 0f);
            officeRadius = args.getInt("location_radius", 100);
            
            Log.d(TAG, "Office location: " + locationName + 
                  " (Lat: " + officeLatitude + 
                  ", Lng: " + officeLongitude + 
                  ", Radius: " + officeRadius + "m)");
        }
        
        loadUserDetails();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentUserAttendanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupCurrentDate();
        setupButtons();
        initializeFaceRecognition();
        
        // Initialize location manager
        locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
        
        // Show office location name
        if (locationName != null && !locationName.isEmpty()) {
            binding.locationNameText.setText("Office: " + locationName);
            binding.locationNameText.setVisibility(View.VISIBLE);
        }
        
        // Request location permissions and start location updates
        requestLocationPermissions();
    }
    
    private void requestLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(requireContext(), 
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), 
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                        LOCATION_PERMISSION_REQUEST_CODE);
            } else {
                startLocationUpdates();
            }
        } else {
            startLocationUpdates();
        }
    }
    
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            // First try to use GPS provider for high accuracy
            boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            
            // Log provider availability
            Log.d(TAG, "Location providers - GPS: " + gpsEnabled + ", Network: " + networkEnabled);
            
            if (gpsEnabled) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        LOCATION_UPDATE_INTERVAL,
                        LOCATION_UPDATE_DISTANCE,
                        this);
                
                Log.d(TAG, "Requested location updates from GPS provider");
            }
            
            // Also use network provider as a backup for faster initial location
            if (networkEnabled) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        LOCATION_UPDATE_INTERVAL,
                        LOCATION_UPDATE_DISTANCE,
                        this);
                
                Log.d(TAG, "Requested location updates from Network provider");
            }
            
            // Check if either provider is available
            if (gpsEnabled || networkEnabled) {
                // Get last known location to immediately update UI
                Location lastKnownLocation = null;
                
                // First try GPS
                if (gpsEnabled) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
                
                // If no GPS location, try network
                if (lastKnownLocation == null && networkEnabled) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                
                if (lastKnownLocation != null) {
                    Log.d(TAG, "Using last known location from provider: " + 
                          (lastKnownLocation.getProvider() != null ? lastKnownLocation.getProvider() : "unknown"));
                    onLocationChanged(lastKnownLocation);
                } else {
                    Log.d(TAG, "No last known location available");
                }
                
                showLocationStatusMessage("Checking your location...");
            } else {
                // No providers available
                Log.e(TAG, "No location providers enabled");
                showLocationStatusMessage("Please enable location services to use check-in/out", true);
                updateCheckInButtonState(false);
            }
        }
    }
    
    private void showLocationStatusMessage(String message) {
        showLocationStatusMessage(message, false);
    }
    
    private void showLocationStatusMessage(String message, boolean indefinite) {
        if (binding != null) {
            if (locationStatusSnackbar != null && locationStatusSnackbar.isShown()) {
                locationStatusSnackbar.dismiss();
            }
            
            locationStatusSnackbar = Snackbar.make(binding.getRoot(), message, 
                    indefinite ? Snackbar.LENGTH_INDEFINITE : Snackbar.LENGTH_LONG);
            locationStatusSnackbar.show();
        }
    }
    
    private void updateCheckInButtonState(boolean isInRange) {
        this.isWithinOfficeRadius = isInRange;
        
        if (binding != null) {
            binding.checkInButton.setEnabled(isInRange);
            binding.checkOutButton.setEnabled(isInRange);
            
            // Update status text
            binding.locationStatusText.setVisibility(View.VISIBLE);
            if (isInRange) {
                binding.locationStatusText.setText("✓ In office range");
                binding.locationStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_success));
            } else {
                binding.locationStatusText.setText("✗ Outside office range");
                binding.locationStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_red));
            }
        }
    }
    
    private void initializeFaceRecognition() {
        try {
            // Initialize the face recognition system
            faceRecognitionSystem = new FaceRecognitionSystem(requireContext());
            faceRecognitionSystem.setVerificationListener(this);
            
            // Set the face recognition status text
            binding.faceRecognitionStatus.setText("Face recognition system initializing...");
            
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                // Get the user details from Firestore
                db.collection("users")
                    .whereEqualTo("uid", currentUser.getUid())  // Use uid instead of sevarthId
                    .limit(1)
                    .get()
                    .addOnSuccessListener(documents -> {
                        if (!documents.isEmpty()) {
                            // Get user document
                            com.google.firebase.firestore.DocumentSnapshot document = documents.getDocuments().get(0);
                            
                            // Get sevarthId and name
                            String sevarthId = document.getString("sevarthId");
                            String firstName = document.getString("firstName");
                            String lastName = document.getString("lastName");
                            String displayName = (firstName != null && lastName != null) ? 
                                    firstName + " " + lastName : "User";
                            
                            // Set user details for face recognition
                            faceRecognitionSystem.setUserDetails(sevarthId, displayName);
                            
                            // Update status text to ready
                            binding.faceRecognitionStatus.setText("Face recognition system ready");
                            binding.faceRecognitionStatus.setBackgroundResource(R.color.green_success);
                        } else {
                            binding.faceRecognitionStatus.setText("Error: User details not found");
                            binding.faceRecognitionStatus.setBackgroundResource(R.color.error_red);
                        }
                    })
                    .addOnFailureListener(e -> {
                        binding.faceRecognitionStatus.setText("Error: Failed to load user details - " + e.getMessage());
                        binding.faceRecognitionStatus.setBackgroundResource(R.color.error_red);
                    });
            } else {
                binding.faceRecognitionStatus.setText("Error: User not logged in");
                binding.faceRecognitionStatus.setBackgroundResource(R.color.error_red);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing face recognition: " + e.getMessage(), e);
            binding.faceRecognitionStatus.setText("Error: Face recognition initialization failed - " + e.getMessage());
            binding.faceRecognitionStatus.setBackgroundResource(R.color.error_red);
        }
    }

    private void loadUserDetails() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            Log.d(TAG, "Loading user details for UID: " + userId);
            
            // Get the user document from Firestore
            db.collection("users")
                .whereEqualTo("uid", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Get the first matching document
                        Log.d(TAG, "User document found");
                        com.google.firebase.firestore.DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                        
                        // Log all fields in the document for debugging
                        Log.d(TAG, "User document data: " + document.getData());
                        
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        
                        // Log individual fields
                        Log.d(TAG, "First Name: " + firstName);
                        Log.d(TAG, "Last Name: " + lastName);
                        
                        if (firstName != null && lastName != null) {
                            userName = firstName + " " + lastName;
                        } else {
                            userName = "Unknown User";
                            Log.e(TAG, "First name or last name is null in Firestore document");
                        }
                        
                        sevarthId = document.getString("sevarthId");
                        Log.d(TAG, "Final userName: " + userName + ", SevarthId: " + sevarthId);
                        
                        // Update UI
                        updateWelcomeText();
                        
                        // Update face recognition system with the correct details
                        if (faceRecognitionSystem != null && sevarthId != null) {
                            faceRecognitionSystem.setUserDetails(sevarthId, userName);
                        }
                    } else {
                        Log.e(TAG, "No user document found for UID: " + userId);
                        // Try to get display name from Firebase Auth as fallback
                        String displayName = currentUser.getDisplayName();
                        if (displayName != null && !displayName.isEmpty()) {
                            userName = displayName;
                            updateWelcomeText();
                        }
                        Toast.makeText(requireContext(), 
                            "Warning: User details incomplete. Please update your profile.", 
                            Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user details: " + e.getMessage());
                    Toast.makeText(requireContext(), 
                        "Error loading user details: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
        } else {
            Log.e(TAG, "No current user found");
            Toast.makeText(requireContext(), 
                "Error: Not logged in. Please login again.", 
                Toast.LENGTH_LONG).show();
        }
    }

    private void updateWelcomeText() {
        if (binding != null && userName != null) {
            binding.welcomeText.setText("Welcome, " + userName);
        }
    }

    private void setupCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US);
        String currentDate = dateFormat.format(Calendar.getInstance().getTime());
        binding.dateText.setText(currentDate);
    }

    private void setupButtons() {
        // Initialize buttons with disabled state until location is verified
        binding.checkInButton.setEnabled(false);
        binding.checkOutButton.setEnabled(false);
        
        // Set click listeners
        binding.checkInButton.setOnClickListener(v -> {
            if (isWithinOfficeRadius) {
                handleCheckIn();
            } else {
                showLocationStatusMessage("You must be within office range to check in", true);
            }
        });
        
        binding.checkOutButton.setOnClickListener(v -> {
            if (isWithinOfficeRadius) {
                handleCheckOut();
            } else {
                showLocationStatusMessage("You must be within office range to check out", true);
            }
        });
        
        binding.viewHistoryButton.setOnClickListener(v -> navigateToAttendanceHistory());
        binding.logoutButton.setOnClickListener(v -> handleLogout());
        
        // Add a long press listener on the location status text to enable attendance for testing
        // This is only for development/testing purposes and should be removed in production
        binding.locationStatusText.setOnLongClickListener(v -> {
            // Toggle the location check for testing
            isWithinOfficeRadius = !isWithinOfficeRadius;
            updateCheckInButtonState(isWithinOfficeRadius);
            
            if (isWithinOfficeRadius) {
                showLocationStatusMessage("TESTING MODE: Location check bypassed", true);
                binding.locationStatusText.setText("✓ TEST MODE: Location check bypassed");
                binding.locationStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_success));
            } else {
                showLocationStatusMessage("TESTING MODE: Normal location check restored", true);
                onLocationChanged(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)); // Reset to actual location
            }
            
            return true; // Consume the long click
        });
    }

    private void handleCheckIn() {
        showFaceVerificationDialog("check_in");
    }

    private void handleCheckOut() {
        showFaceVerificationDialog("check_out");
    }
    
    private void showFaceVerificationDialog(String attendanceType) {
        // Create a dialog with a camera preview view
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_face_verification, null);
        PreviewView previewView = dialogView.findViewById(R.id.cameraPreviewView);
        final android.widget.TextView statusText = dialogView.findViewById(R.id.statusText);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Face Verification");
        builder.setView(dialogView);
        builder.setCancelable(false);
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
            if (faceRecognitionSystem != null) {
                faceRecognitionSystem.shutdown();
            }
        });
        
        // Show the dialog
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        // Initialize camera immediately
        if (faceRecognitionSystem != null) {
            statusText.setText("Preparing camera...");
            
            // Initialize camera and start verification process immediately
            faceRecognitionSystem.initializeCamera(this, previewView);
            
            // Reduced delay to 500ms (from 1500ms) - just enough time for camera to warm up
            new android.os.Handler().postDelayed(() -> {
                if (isAdded() && faceRecognitionSystem != null) {
                    statusText.setText("Capturing face image...");
                    
                    // Use the camera to capture an image with optimized settings
                    faceRecognitionSystem.captureImage(bitmap -> {
                        if (bitmap == null) {
                            statusText.setText("Failed to capture image. Please try again.");
                            return;
                        }
                        
                        statusText.setText("Verifying...");
                        
                        // Verify face if sevarthId exists
                        if (sevarthId != null) {
                            // Verify face with API using optimized callback handling
                            faceRepository.verifyFace(sevarthId, bitmap, new FaceRecognitionRepository.RepositoryCallback<FaceVerificationResponse>() {
                                @Override
                                public void onSuccess(FaceVerificationResponse result) {
                                    if (result.isVerified()) {
                                        statusText.setText("Verified! Recording attendance...");
                                        
                                        // Mark attendance immediately after verification
                                        faceRepository.markAttendance(sevarthId, attendanceType, result.getConfidence(), locationId,
                                            new FaceRecognitionRepository.RepositoryCallback<AttendanceResponse>() {
                                                @Override
                                                public void onSuccess(AttendanceResponse response) {
                                                    // The backend has already stored the attendance, no need to store in Firestore again
                                                    dialog.dismiss();
                                                    String attendanceText = attendanceType.equals("check_in") ? "Check-In" : "Check-Out";
                                                    
                                                    // Update UI with success
                                                    requireActivity().runOnUiThread(() -> {
                                                        Snackbar.make(binding.getRoot(), 
                                                                attendanceText + " recorded at " + response.getTime(),
                                                                Snackbar.LENGTH_LONG)
                                                                .setBackgroundTint(requireContext().getColor(R.color.green_success))
                                                                .show();
                                                        
                                                        binding.faceRecognitionStatus.setText("✓ " + attendanceText + " successful");
                                                        binding.faceRecognitionStatus.setBackgroundResource(R.color.green_success);
                                                    });
                                                }
                                                
                                                @Override
                                                public void onError(String errorMessage) {
                                                    dialog.dismiss();
                                                    requireActivity().runOnUiThread(() -> {
                                                        handleError("Failed to mark attendance: " + errorMessage);
                                                    });
                                                }
                                            });
                                    } else {
                                        dialog.dismiss();
                                        requireActivity().runOnUiThread(() -> {
                                            handleError("Face verification failed: " + result.getMessage());
                                        });
                                    }
                                }
                                
                                @Override
                                public void onError(String errorMessage) {
                                    dialog.dismiss();
                                    requireActivity().runOnUiThread(() -> {
                                        handleError("Verification error: " + errorMessage);
                                    });
                                }
                            });
                        } else {
                            dialog.dismiss();
                            handleError("Sevarth ID not found. Please try logging in again.");
                        }
                    });
                }
            }, 500); // Reduced delay from 1500ms to 500ms
        } else {
            Toast.makeText(requireContext(), "Face recognition system not initialized", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }
    }

    // Helper method to handle errors consistently
    private void handleError(String errorMessage) {
        Log.e(TAG, "Error in attendance process: " + errorMessage);
        Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG)
                .setBackgroundTint(requireContext().getColor(R.color.error_red))
                .show();
        
        binding.faceRecognitionStatus.setText("Error: " + errorMessage);
        binding.faceRecognitionStatus.setBackgroundResource(R.color.error_red);
    }

    private void navigateToAttendanceHistory() {
        Intent intent = new Intent(requireActivity(), AttendanceHistoryActivity.class);
        startActivity(intent);
    }

    private void handleLogout() {
        mAuth.signOut();
        
        // Navigate back to LoginActivity instead of just finishing
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (faceRecognitionSystem != null) {
            faceRecognitionSystem.shutdown();
        }
        
        // Stop location updates
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        
        // Clean up Snackbar if showing
        if (locationStatusSnackbar != null && locationStatusSnackbar.isShown()) {
            locationStatusSnackbar.dismiss();
        }
        
        binding = null;
    }
    
    // Implementation of FaceRecognitionSystem.VerificationListener
    
    @Override
    public void onVerificationStarted() {
        if (binding != null) {
            binding.faceRecognitionStatus.setText("Verification started...");
            binding.faceRecognitionStatus.setBackgroundResource(R.color.blue_primary);
        }
    }

    @Override
    public void onReferenceImageLoaded() {
        if (binding != null) {
            binding.faceRecognitionStatus.setText("Reference image loaded. Preparing camera...");
        }
    }

    @Override
    public void onFaceCaptured() {
        if (binding != null) {
            binding.faceRecognitionStatus.setText("Face captured. Verifying identity...");
        }
    }

    @Override
    public void onVerificationComplete(boolean isVerified, float confidence) {
        if (binding != null) {
            if (isVerified) {
                int confidencePercent = (int) (confidence * 100);
                binding.faceRecognitionStatus.setText("Identity verified! (Confidence: " + confidencePercent + "%)");
                binding.faceRecognitionStatus.setBackgroundResource(R.color.green_success);
            } else {
                binding.faceRecognitionStatus.setText("Verification failed. Face does not match reference image.");
                binding.faceRecognitionStatus.setBackgroundResource(R.color.error_red);
            }
        }
    }

    @Override
    public void onVerificationError(String errorMessage) {
        if (binding != null) {
            binding.faceRecognitionStatus.setText("Error: " + errorMessage);
            binding.faceRecognitionStatus.setBackgroundResource(R.color.error_red);
        }
    }

    @Override
    public void onAttendanceMarked(boolean success, String message) {
        if (getContext() != null) {
            if (success) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(requireContext().getColor(R.color.green_success))
                    .show();
            } else {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(requireContext().getColor(R.color.error_red))
                    .show();
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null && binding != null) {
            // Calculate distance from office
            float[] results = new float[1];
            Location.distanceBetween(
                    location.getLatitude(), location.getLongitude(),
                    officeLatitude, officeLongitude,
                    results);
            
            float distanceToOffice = results[0]; // in meters
            
            Log.d(TAG, "Current location: Lat=" + location.getLatitude() + 
                    ", Lng=" + location.getLongitude() + 
                    ", Distance to office: " + distanceToOffice + "m, " +
                    "Office radius: " + officeRadius + "m");
            
            // Add a buffer to the radius to account for GPS inaccuracy (10% of radius or 10 meters, whichever is greater)
            int locationBuffer = Math.max(10, (int)(officeRadius * 0.1));
            int adjustedRadius = officeRadius + locationBuffer;
            
            // Check if within adjusted radius
            boolean isInRange = distanceToOffice <= adjustedRadius;
            
            // Update UI based on location
            updateCheckInButtonState(isInRange);
            
            if (isInRange) {
                // For testing purposes, enable check-in/out temporarily by forcing isWithinOfficeRadius to true
                // Remove this override in production
                updateCheckInButtonState(true);
                
                // Show appropriate message
                if (distanceToOffice <= officeRadius) {
                    showLocationStatusMessage("You are within office range");
                } else {
                    showLocationStatusMessage("You are near office boundary (buffer zone)");
                }
            } else {
                showLocationStatusMessage("You are " + Math.round(distanceToOffice - officeRadius) + 
                        "m outside office range");
            }
        }
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Required for LocationListener interface but deprecated in API 29+
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            showLocationStatusMessage("GPS enabled, checking location...");
        }
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            showLocationStatusMessage("Please enable GPS to use check-in/out", true);
            updateCheckInButtonState(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                showLocationStatusMessage("Location permission denied. Check-in/out unavailable.", true);
                updateCheckInButtonState(false);
            }
        }
    }
} 