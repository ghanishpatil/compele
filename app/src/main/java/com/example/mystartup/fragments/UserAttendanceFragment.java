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
import com.example.mystartup.api.AttendanceRequest;
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
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.List;
import java.util.Map;

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
import android.content.SharedPreferences;
import android.widget.TextView;
import android.app.AlertDialog;

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
        
        try {
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
                
                Log.d(TAG, "Office location loaded: " + locationName + 
                      " (ID: " + locationId + ", " +
                      "Lat: " + officeLatitude + 
                      ", Lng: " + officeLongitude + 
                      ", Radius: " + officeRadius + "m)");
            } else {
                Log.e(TAG, "No location arguments provided to fragment!");
                // Initialize with default values
                locationId = "";
                locationName = "";
                officeLatitude = 0;
                officeLongitude = 0;
                officeRadius = 100;
            }
            
            // Ensure we don't crash even if user data fails to load
            try {
                loadUserDetails();
            } catch (Exception e) {
                Log.e(TAG, "Error loading user details: " + e.getMessage(), e);
                // Continue with default/empty values
                userId = "";
                userName = "User";
                sevarthId = "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in fragment onCreate: " + e.getMessage(), e);
            // Initialize with default values
            locationId = "";
            locationName = "";
            officeLatitude = 0;
            officeLongitude = 0;
            officeRadius = 100;
            userId = "";
            userName = "User";
            sevarthId = "";
        }
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
        
        try {
            setupCurrentDate();
            setupButtons();
            
            try {
                initializeFaceRecognition();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing face recognition: " + e.getMessage(), e);
                if (binding != null) {
                    binding.faceRecognitionStatus.setVisibility(View.GONE);
                }
            }
            
            // Initialize location manager
            try {
                locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
            } catch (Exception e) {
                Log.e(TAG, "Error getting location manager: " + e.getMessage(), e);
            }
            
            // Show office location name from our arguments, not from user data
            if (binding != null && locationName != null && !locationName.isEmpty()) {
                Log.d(TAG, "Setting location name in view: " + locationName);
                binding.locationNameText.setText("Office: " + locationName);
                binding.locationNameText.setVisibility(View.VISIBLE);
            } else {
                Log.w(TAG, "Location name is empty or null");
            }
            
            // Hide any location error message that might be shown
            hideLocationErrorMessage();
            
            // Request location permissions and start location updates
            try {
                requestLocationPermissions();
            } catch (Exception e) {
                Log.e(TAG, "Error requesting location permissions: " + e.getMessage(), e);
                // Do NOT enable buttons automatically - it should depend on actual location
                updateCheckInButtonState(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage(), e);
            // Do NOT enable buttons automatically - it should depend on actual location
            try {
                if (binding != null) {
                    binding.checkInButton.setEnabled(false);
                    binding.checkOutButton.setEnabled(false);
                }
            } catch (Exception ex) {
                Log.e(TAG, "Critical error setting up UI: " + ex.getMessage(), ex);
            }
        }
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
        // Store the actual location status
        this.isWithinOfficeRadius = isInRange;
        
        if (binding != null) {
            // Only enable buttons if user is in range
            binding.checkInButton.setEnabled(isInRange);
            binding.checkOutButton.setEnabled(isInRange);
            
            // Show appropriate status
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
                            // Don't show an error in the UI, just log it
                            Log.e(TAG, "User details not found in Firestore for face verification");
                            binding.faceRecognitionStatus.setVisibility(View.GONE);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Don't show an error in the UI, just log it
                        Log.e(TAG, "Failed to load user details for face verification: " + e.getMessage());
                        binding.faceRecognitionStatus.setVisibility(View.GONE);
                    });
            } else {
                // Don't show an error in the UI, just log it
                Log.e(TAG, "User not logged in for face verification");
                binding.faceRecognitionStatus.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing face recognition: " + e.getMessage(), e);
            binding.faceRecognitionStatus.setVisibility(View.GONE);
        }
    }

    private void loadUserDetails() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        
        if (user == null) {
            Log.e(TAG, "User not logged in. Redirecting to login");
            handleLogout();
            return;
        }
        
        Log.d(TAG, "Loading user details for Firebase UID: " + user.getUid());
        
        SharedPreferences prefs = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        sevarthId = prefs.getString("sevarth_id", null);
            
        // Debug: Check if sevarthId is available
        Log.d(TAG, "Sevarth ID from SharedPreferences: " + sevarthId);
        
        if (sevarthId == null) {
            // If sevarthId is not available in prefs, try to get it from Firebase user's email
            // Many emails are formatted as sevarthId@example.com
            String email = user.getEmail();
            if (email != null && email.contains("@")) {
                sevarthId = email.substring(0, email.indexOf('@'));
                Log.d(TAG, "Extracted Sevarth ID from email: " + sevarthId);
                
                // Save this to preferences for future use
                prefs.edit().putString("sevarth_id", sevarthId).apply();
            } else {
                Log.e(TAG, "Could not determine Sevarth ID");
                Toast.makeText(requireContext(), "User profile incomplete. Please login again.", Toast.LENGTH_LONG).show();
                handleLogout();
                return;
            }
        }
        
        // Try to load user by Firebase UID first (most reliable)
        db.collection("users")
            .whereEqualTo("uid", user.getUid())
            .limit(1)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    processUserDocuments(queryDocumentSnapshots);
                } else {
                    // If not found by UID, try by sevarthId
                    Log.d(TAG, "User not found by UID, trying sevarthId: " + sevarthId);
            db.collection("users")
                        .whereEqualTo("sevarthId", sevarthId)
                        .limit(1)
                .get()
                        .addOnSuccessListener(snapshots -> {
                            if (!snapshots.isEmpty()) {
                                processUserDocuments(snapshots);
                                
                                // Update this document with the correct UID if needed
                                for (QueryDocumentSnapshot doc : snapshots) {
                                    if (!doc.contains("uid") || doc.getString("uid") == null) {
                                        doc.getReference().update("uid", user.getUid())
                                            .addOnSuccessListener(aVoid -> 
                                                Log.d(TAG, "Updated user document with UID"));
                                    }
                                }
                            } else {
                                // No user document found by any method
                                Log.e(TAG, "User not found in Firestore");
                                Toast.makeText(requireContext(), "User profile not found. Please contact admin.", Toast.LENGTH_LONG).show();
                                handleLogout();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching user by sevarthId: " + e.getMessage(), e);
                            Toast.makeText(requireContext(), "Failed to load user data. Please try again later.", Toast.LENGTH_SHORT).show();
                        });
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching user data: " + e.getMessage(), e);
                Toast.makeText(requireContext(), "Failed to load user data. Please try again later.", Toast.LENGTH_SHORT).show();
            });
    }
    
    // Helper method to process user documents
    private void processUserDocuments(com.google.firebase.firestore.QuerySnapshot snapshots) {
        // Get user document
        for (QueryDocumentSnapshot document : snapshots) {
            Map<String, Object> userData = document.getData();
                
            // Extract all user details explicitly
            String firstName = (String) userData.get("firstName");
            String lastName = (String) userData.get("lastName");
            userId = (String) userData.get("uid");
            
            // If sevarthId wasn't properly set in SharedPreferences, get it from Firestore
            if (sevarthId == null || sevarthId.isEmpty()) {
                sevarthId = (String) userData.get("sevarthId");
                if (sevarthId != null) {
                    // Save to preferences
                    SharedPreferences prefs = requireActivity().getSharedPreferences(
                        "user_prefs", Context.MODE_PRIVATE);
                    prefs.edit().putString("sevarth_id", sevarthId).apply();
                }
            }
            
            // Create a proper user name
            userName = "";
            if (firstName != null && !firstName.isEmpty()) {
                userName += firstName;
            }
            if (lastName != null && !lastName.isEmpty()) {
                if (!userName.isEmpty()) userName += " ";
                userName += lastName;
            }
            
            // Debug: log user details
            Log.d(TAG, "User data loaded: sevarthId=" + sevarthId + 
                  ", name='" + userName + "', uid=" + userId);
            
            // Use Firebase UID if the one from Firestore is missing
            if (userId == null || userId.isEmpty()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    userId = user.getUid();
                    Log.w(TAG, "Using Firebase Auth UID as fallback: " + userId);
                } else {
                    Log.e(TAG, "User UID missing in Firestore and Firebase Auth");
                    Toast.makeText(requireContext(), "User profile incomplete. Contact administrator.", Toast.LENGTH_LONG).show();
                }
            }
            
            if (userName == null || userName.isEmpty()) {
                userName = "User"; // Fallback to generic name if missing
                Log.w(TAG, "User name missing, using default");
            }
            
            // DO NOT override the locationId and locationName that were passed to the fragment
            // We should respect the user's selection from LocationSelectionActivity
            Log.d(TAG, "Keeping selected location: " + locationName + " (ID: " + locationId + ")");
        }
        
        updateWelcomeText();
    }

    // Method to update location details from Firestore
    private void updateLocationDetails(String locationId) {
        db.collection("office_locations")
            .document(locationId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Get location coordinates
                    Double latitude = documentSnapshot.getDouble("latitude");
                    Double longitude = documentSnapshot.getDouble("longitude");
                    Long radiusLong = documentSnapshot.getLong("radius");
                    
                    if (latitude != null && longitude != null) {
                        officeLatitude = latitude;
                        officeLongitude = longitude;
                        
                        // Set default radius if not specified
                        officeRadius = (radiusLong != null) ? radiusLong.intValue() : 100;
                        
                        Log.d(TAG, "Office location set: lat=" + officeLatitude + 
                              ", long=" + officeLongitude + ", radius=" + officeRadius + "m");
                        
                        // Hide any existing location error message
                        hideLocationErrorMessage();
                        
                        // Start location tracking to check if user is at office
                        startLocationUpdates();
                    } else {
                        Log.e(TAG, "Invalid location coordinates in Firestore");
                        // Do not show location error message, just log it
                        // Instead of showing error, enable the buttons anyway
                        updateCheckInButtonState(true);
                        startLocationUpdates();
                    }
                } else {
                    Log.e(TAG, "Location document not found: " + locationId);
                    // Do not show location error message, just log it
                    // Instead of showing error, enable the buttons anyway
                    updateCheckInButtonState(true);
                    startLocationUpdates();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching location details: " + e.getMessage(), e);
                // Do not show location error message, just log it
                // Instead of showing error, enable the buttons anyway
                updateCheckInButtonState(true);
                startLocationUpdates();
            });
    }

    // Helper method to show location error message in a card
    private void showLocationErrorMessage(String message) {
        if (binding != null && getActivity() != null) {
            try {
                // First, try to find if an error message card already exists
                View existingErrorCard = getView().findViewById(R.id.location_error_card);
                
                if (existingErrorCard != null) {
                    // Card exists, just update the message
                    TextView errorText = existingErrorCard.findViewById(R.id.location_error_text);
                    if (errorText != null) {
                        errorText.setText(message);
                    }
                    existingErrorCard.setVisibility(View.VISIBLE);
        } else {
                    // Need to create a card programmatically
                    // Since the card doesn't exist in XML, we'll use a Snackbar instead
                    Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_INDEFINITE)
                        .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.error_red))
                        .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                        .setAction("Dismiss", v -> {})
                        .show();
                }
                
                // Disable check-in/out buttons since location is invalid
                binding.checkInButton.setEnabled(false);
                binding.checkOutButton.setEnabled(false);
                binding.locationStatusText.setText("✗ Location data missing");
                binding.locationStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_red));
                
            } catch (Exception e) {
                Log.e(TAG, "Error showing location error message", e);
            }
        }
    }
    
    // Helper method to hide location error message
    private void hideLocationErrorMessage() {
        if (binding != null && getActivity() != null) {
            try {
                View existingErrorCard = getView().findViewById(R.id.location_error_card);
                if (existingErrorCard != null) {
                    existingErrorCard.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error hiding location error message", e);
            }
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
    }

    private void handleCheckIn() {
        showFaceVerificationDialog("check_in");
    }

    private void handleCheckOut() {
        showFaceVerificationDialog("check_out");
    }
    
    private void showFaceVerificationDialog(String attendanceType) {
        if (locationId == null || locationId.isEmpty()) {
            Toast.makeText(requireContext(), "No office location assigned. Contact administrator.", Toast.LENGTH_LONG).show();
            return;
        }
        
        // Ensure we have at least the sevarthId, which is the minimum required
        if (sevarthId == null || sevarthId.isEmpty()) {
            Log.e(TAG, "Missing sevarthId. Unable to proceed with verification.");
            Toast.makeText(requireContext(), "Missing essential user data. Please login again.", Toast.LENGTH_LONG).show();
            handleLogout();
            return;
        }
        
        // If userId is missing, try to get it from Firebase Auth
        if (userId == null || userId.isEmpty()) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
                Log.d(TAG, "Retrieved user ID from Firebase Auth: " + userId);
            } else {
                Log.w(TAG, "Unable to get userId from Firebase Auth, but will continue with attendance");
                // We'll continue with null userId, hoping the server can handle it
            }
        }
        
        // If userName is missing, use a default
        if (userName == null || userName.isEmpty()) {
            userName = "User"; // Default name
            Log.w(TAG, "Using default user name because actual name is missing");
        }
        
        // Also ensure we have a valid location name
        if (locationName == null || locationName.isEmpty()) {
            // Try to get location name from SharedPreferences
            SharedPreferences prefs = requireActivity().getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE);
            locationName = prefs.getString("selected_location_name", "Unknown Office");
            Log.d(TAG, "Retrieved location name from SharedPreferences: " + locationName);
        }
        
        // Log the actual location info being used
        Log.d(TAG, "Using location for attendance: ID=" + locationId + ", Name=" + locationName);
        
        // Create and show dialog
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_face_verification, null);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();
            
        // Initialize views
        PreviewView previewView = dialogView.findViewById(R.id.cameraPreviewView);
        TextView statusText = dialogView.findViewById(R.id.statusText);
        
        // Show dialog
        dialog.show();
        
        // Initialize face recognition system if needed
        if (faceRecognitionSystem == null) {
            faceRecognitionSystem = new FaceRecognitionSystem(requireContext());
            faceRecognitionSystem.setVerificationListener(this);
        }
        
        // Debug: Log user details
        Log.d(TAG, "Starting verification with: sevarthId=" + sevarthId + 
              ", name='" + userName + "', userId=" + userId + 
              ", locationId=" + locationId +
              ", locationName=" + locationName);
              
        // Save final variables for use in lambda
        final String finalLocationId = locationId;
        final String finalLocationName = locationName;
        final String finalUserName = userName;
        final String finalUserId = userId;
              
        // Slight delay to allow dialog to fully render
        new android.os.Handler().postDelayed(() -> {
            if (isAdded() && faceRecognitionSystem != null) {
                statusText.setText("Initializing camera...");
            
                // Initialize camera and start verification process
                faceRecognitionSystem.initializeCamera(this, previewView);
                
                // Increased delay to 1500ms to give camera more time to initialize
                new android.os.Handler().postDelayed(() -> {
                    if (isAdded() && faceRecognitionSystem != null) {
                        statusText.setText("Capturing face image...");
                        
                        // Attempt to capture the image
                        captureImageWithRetry(bitmap -> {
                            if (bitmap == null) {
                                statusText.setText("Failed to capture image. Please try again.");
                                new android.os.Handler().postDelayed(() -> {
                                    dialog.dismiss();
                                }, 2000);
                                return;
                            }
                            
                            statusText.setText("Verifying...");
                            
                            // Log debug information
                            Log.d(TAG, "About to verify face. User data: sevarthId=" + sevarthId + 
                                  ", userName=" + finalUserName + ", userId=" + finalUserId + 
                                  ", locationId=" + finalLocationId + 
                                  ", locationName=" + finalLocationName);
                            
                            // Verify face if sevarthId exists
                            faceRepository.verifyFace(sevarthId, bitmap, new FaceRecognitionRepository.RepositoryCallback<FaceVerificationResponse>() {
                                @Override
                                public void onSuccess(FaceVerificationResponse result) {
                                    if (result.isVerified()) {
                                        statusText.setText("Verified! Recording attendance...");
                                        
                                        // Mark attendance immediately after verification, providing all user details
                                        AttendanceRequest request = new AttendanceRequest(
                                            sevarthId,
                                            attendanceType,
                                            result.getConfidence(),
                                            finalLocationId,
                                            finalUserName,
                                            finalUserId);
                                            
                                        // Also add locationName to the request
                                        request.setLocationName(finalLocationName);
                                        
                                        faceRepository.markAttendanceWithRequest(
                                            request,
                                            new FaceRecognitionRepository.RepositoryCallback<AttendanceResponse>() {
                                                @Override
                                                public void onSuccess(AttendanceResponse response) {
                                                    dialog.dismiss();
                                                    onAttendanceMarked(true, response.getMessage());
                                                }
                                                
                                                @Override
                                                public void onError(String errorMessage) {
                                                    dialog.dismiss();
                                                    requireActivity().runOnUiThread(() -> {
                                                        // Check if the error is about incomplete data
                                                        if (errorMessage.contains("User data incomplete")) {
                                                            // Trigger user reload to ensure we have the latest data
                                                            loadUserDetails();
                                                            
                                                            // Show a more specific error
                                                            handleError("Your profile data is incomplete. Please update your profile.");
                                                        } else {
                                                        handleError("Failed to mark attendance: " + errorMessage);
                                                        }
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
                                        handleError("Face verification error: " + errorMessage);
                                    });
                                }
                            });
                        }, dialog, statusText, 0);
                    }
                }, 1500); // Increased from 500ms to 1500ms to allow camera to fully initialize
            } else {
                Toast.makeText(requireContext(), "Face recognition system not initialized", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        }, 500);
    }
    
    // New method to handle image capture with retry
    private void captureImageWithRetry(FaceRecognitionSystem.ImageCaptureCallback callback, 
                                      androidx.appcompat.app.AlertDialog dialog,
                                      TextView statusText,
                                      int retryCount) {
        // Maximum retry attempts
        final int MAX_RETRIES = 2;
        
        if (retryCount > MAX_RETRIES) {
            Log.e(TAG, "Exceeded maximum retry attempts for image capture");
            statusText.setText("Failed to capture image after multiple attempts");
            new android.os.Handler().postDelayed(() -> {
                dialog.dismiss();
            }, 2000);
            return;
        }
        
        // Log retry attempt
        if (retryCount > 0) {
            Log.d(TAG, "Retrying image capture, attempt " + retryCount);
            statusText.setText("Retrying image capture...");
        }
        
        // Attempt to capture the image
        faceRecognitionSystem.captureImage(bitmap -> {
            if (bitmap == null) {
                Log.w(TAG, "Image capture failed, retry count: " + retryCount);
                
                // Wait a bit and then retry
                new android.os.Handler().postDelayed(() -> {
                    captureImageWithRetry(callback, dialog, statusText, retryCount + 1);
                }, 1000);
            } else {
                // Success - pass the bitmap to the original callback
                callback.onImageCaptured(bitmap);
            }
        });
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
        try {
            if (location != null) {
                Log.d(TAG, "Location update received: " + location.getLatitude() + ", " + location.getLongitude());
                
                // Create office location
                Location officeLocation = new Location("");
                
                // Only calculate distance if we have valid office coordinates
                if (officeLatitude != 0 && officeLongitude != 0) {
                    officeLocation.setLatitude(officeLatitude);
                    officeLocation.setLongitude(officeLongitude);
                    
                    // Calculate distance to office in meters
                    float distanceToOffice = location.distanceTo(officeLocation);
                    
                    Log.d(TAG, "Distance to office: " + distanceToOffice + "m (Allowed radius: " + officeRadius + "m)");
                    
                    // Check if within radius
                    boolean isInRange = distanceToOffice <= officeRadius;
                    
                    // Update UI based on location
                    updateCheckInButtonState(isInRange);
                    
                    if (isInRange) {
                        showLocationStatusMessage("You are within office range (" + (int)distanceToOffice + "m from office)");
                    } else {
                        showLocationStatusMessage("You are outside office range (" + (int)distanceToOffice + "m from office, radius: " + officeRadius + "m)");
                    }
                } else {
                    // If we don't have valid office coordinates, do NOT default to allowing check-in
                    Log.e(TAG, "No valid office coordinates available, disabling check-in");
                    isWithinOfficeRadius = false;
                    updateCheckInButtonState(false);
                    showLocationStatusMessage("Cannot verify location: missing office coordinates", true);
                }
            } else {
                // Handle null location by disabling check-in
                Log.e(TAG, "Received null location, disabling check-in");
                isWithinOfficeRadius = false;
                updateCheckInButtonState(false);
                showLocationStatusMessage("Cannot get your current location", true);
            }
        } catch (Exception e) {
            // Handle any unexpected errors to prevent crash
            Log.e(TAG, "Error processing location update: " + e.getMessage(), e);
            // Disable check-in as fallback
            isWithinOfficeRadius = false;
            try {
                updateCheckInButtonState(false);
                showLocationStatusMessage("Error processing location: " + e.getMessage(), true);
            } catch (Exception ex) {
                Log.e(TAG, "Critical error updating UI: " + ex.getMessage(), ex);
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