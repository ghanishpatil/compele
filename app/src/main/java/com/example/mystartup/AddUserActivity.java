package com.example.mystartup;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.Toast;
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mystartup.databinding.ActivityAddUserBinding;
import com.example.mystartup.models.OfficeLocation;
import com.example.mystartup.models.User;
import com.example.mystartup.utils.FirebaseUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageMetadata;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.net.Uri;
import android.provider.MediaStore;

public class AddUserActivity extends AppCompatActivity {

    private static final String TAG = "AddUserActivity";
    private static final int CAMERA_REQUEST_CODE = 100;
    
    private ActivityAddUserBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private Calendar calendar;
    private List<OfficeLocation> locationList;
    private Map<String, String> locationMap;
    private Set<String> selectedLocationIds;
    private Set<String> selectedLocationNames;
    private String editId = null;
    private SharedPreferences prefs;
    private static final String PREF_NAME = "AuthPrefs";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private String capturedImagePath;
    private Uri selectedImageUri;
    
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                if (data != null) {
                    capturedImagePath = data.getStringExtra("image_path");
                    if (capturedImagePath != null) {
                        // Image captured successfully, proceed with saving user data
                        selectedImageUri = null; // Clear selected image if camera was used
                        binding.imagePreviewText.setText("Face image captured");
                        binding.imagePreviewText.setVisibility(View.VISIBLE);
                        saveUserToFirebase();
                    }
                }
            } else {
                Toast.makeText(this, "Face image capture cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    );
    
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
        new ActivityResultContracts.GetContent(),
        uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                capturedImagePath = null; // Clear captured path if gallery was used
                binding.imagePreviewText.setText("Image selected from gallery");
                binding.imagePreviewText.setVisibility(View.VISIBLE);
                // Don't save to Firebase yet, we'll do that when user clicks Next
            }
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddUserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        
        // Initialize preferences
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // Initialize calendar for date picker
        calendar = Calendar.getInstance();
        
        // Initialize location collections
        locationList = new ArrayList<>();
        locationMap = new HashMap<>();
        selectedLocationIds = new HashSet<>();
        selectedLocationNames = new HashSet<>();

        // Check if we're editing an existing user
        if (getIntent().hasExtra("USER_ID")) {
            editId = getIntent().getStringExtra("USER_ID");
            setupForEditing(editId);
            
            // Change title and button text for editing
            binding.topBarTitle.setText("Edit User");
            binding.nextButton.setText("UPDATE");
        }

        setupDatePicker();
        loadOfficeLocations();
        setupClickListeners();
    }

    private void setupDatePicker() {
        DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateDateInView();
            }
        };

        binding.dobEditText.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    AddUserActivity.this,
                    dateSetListener,
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            datePickerDialog.show();
        });
    }

    private void updateDateInView() {
        String myFormat = "dd/MM/yyyy";
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.getDefault());
        binding.dobEditText.setText(sdf.format(calendar.getTime()));
    }

    private void loadOfficeLocations() {
        db.collection("office_locations")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    locationList.clear();
                    locationMap.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        OfficeLocation location = document.toObject(OfficeLocation.class);
                        locationList.add(location);
                        locationMap.put(location.getName(), location.getId());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading office locations", e);
                    Toast.makeText(this, "Failed to load office locations", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupForEditing(String userId) {
        Log.d(TAG, "Setting up for editing user with ID: " + userId);
        
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Log.d(TAG, "User document found, loading data...");
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            binding.sevarthIdEditText.setText(user.getSevarthId());
                            binding.firstNameEditText.setText(user.getFirstName());
                            binding.lastNameEditText.setText(user.getLastName());
                            
                            // Set gender
                            if ("Male".equals(user.getGender())) {
                                binding.maleRadioButton.setChecked(true);
                            } else if ("Female".equals(user.getGender())) {
                                binding.femaleRadioButton.setChecked(true);
                            }
                            
                            // Set date of birth
                            binding.dobEditText.setText(user.getDateOfBirth());
                            
                            // Set contact info
                            binding.phoneEditText.setText(user.getPhoneNumber());
                            binding.emailEditText.setText(user.getEmail());
                            
                            // Set locations
                            List<String> userLocationIds = user.getLocationIds();
                            List<String> userLocationNames = user.getLocationNames();
                            if (userLocationIds != null && userLocationNames != null) {
                                selectedLocationIds.addAll(userLocationIds);
                                selectedLocationNames.addAll(userLocationNames);
                                updateLocationDisplay();
                            }
                            
                            // If we're editing, disable the Sevarth ID field
                            binding.sevarthIdEditText.setEnabled(false);
                            binding.sevarthIdLayout.setEnabled(false);
                            
                            // Hide the password field in edit mode
                            binding.passwordLayout.setVisibility(View.GONE);
                        } else {
                            Log.e(TAG, "User document exists but couldn't be converted to User object");
                            Toast.makeText(this, "Error loading user data: Invalid format", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "User document not found for ID: " + userId);
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user data: " + e.getMessage(), e);
                    Toast.makeText(this, "Failed to load user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupClickListeners() {
        // Back button
        binding.backButton.setOnClickListener(v -> finish());
        
        // Cancel button
        binding.cancelButton.setOnClickListener(v -> finish());
        
        // Next/Add button
        binding.nextButton.setOnClickListener(v -> {
            if (validateInputs()) {
                if (selectedImageUri != null) {
                    // User selected an image from gallery
                    saveUserToFirebase();
                } else if (editId != null) {
                    // If we're editing and don't want to change the image, skip camera
                    new AlertDialog.Builder(this)
                        .setTitle("Face Image")
                        .setMessage("Do you want to update the face image?")
                        .setPositiveButton("Yes", (dialog, which) -> showImageSourceOptions())
                        .setNegativeButton("No", (dialog, which) -> {
                            // Skip camera and proceed
                            saveUserToFirebase();
                        })
                        .show();
                } else {
                    // For new users, always get a face image
                    showImageSourceOptions();
                }
            }
        });

        // Setup location selection
        binding.locationEditText.setOnClickListener(v -> showLocationSelectionDialog());
        
        // Setup upload image button
        binding.uploadImageButton.setOnClickListener(v -> galleryLauncher.launch("image/*"));
    }

    private void showLocationSelectionDialog() {
        if (locationList.isEmpty()) {
            Toast.makeText(this, "No locations available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create arrays for the dialog
        String[] locationNames = new String[locationList.size()];
        boolean[] checkedItems = new boolean[locationList.size()];

        // Fill the arrays
        for (int i = 0; i < locationList.size(); i++) {
            OfficeLocation location = locationList.get(i);
            locationNames[i] = location.getName();
            checkedItems[i] = selectedLocationIds.contains(location.getId());
        }

        // Create and show the dialog
        new AlertDialog.Builder(this)
            .setTitle("Select Locations")
            .setMultiChoiceItems(locationNames, checkedItems, (dialog, which, isChecked) -> {
                OfficeLocation location = locationList.get(which);
                if (isChecked) {
                    selectedLocationIds.add(location.getId());
                    selectedLocationNames.add(location.getName());
                } else {
                    selectedLocationIds.remove(location.getId());
                    selectedLocationNames.remove(location.getName());
                }
            })
            .setPositiveButton("OK", (dialog, which) -> {
                // Update the EditText with selected locations
                updateLocationDisplay();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateLocationDisplay() {
        if (selectedLocationNames.isEmpty()) {
            binding.locationEditText.setText("");
        } else {
            binding.locationEditText.setText(TextUtils.join(", ", selectedLocationNames));
        }
    }

    private boolean validateInputs() {
        boolean isValid = true;
        
        // Validate Sevarth ID
        String sevarthId = binding.sevarthIdEditText.getText().toString().trim();
        if (TextUtils.isEmpty(sevarthId)) {
            binding.sevarthIdLayout.setError("Sevarth ID is required");
            isValid = false;
        } else if (!sevarthId.matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}$")) {
            binding.sevarthIdLayout.setError("Invalid Sevarth ID format (must contain letters and numbers, min 6 chars)");
            isValid = false;
        } else {
            binding.sevarthIdLayout.setError(null);
        }
        
        // Validate First Name
        String firstName = binding.firstNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(firstName)) {
            binding.firstNameLayout.setError("First name is required");
            isValid = false;
        } else if (!firstName.matches("^[A-Za-z\\s]+$")) {
            binding.firstNameLayout.setError("First name should contain only letters");
            isValid = false;
        } else {
            binding.firstNameLayout.setError(null);
        }
        
        // Validate Last Name
        String lastName = binding.lastNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(lastName)) {
            binding.lastNameLayout.setError("Last name is required");
            isValid = false;
        } else if (!lastName.matches("^[A-Za-z\\s]+$")) {
            binding.lastNameLayout.setError("Last name should contain only letters");
            isValid = false;
        } else {
            binding.lastNameLayout.setError(null);
        }
        
        // Validate Gender (at least one radio button should be selected)
        if (binding.genderRadioGroup.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "Please select a gender", Toast.LENGTH_SHORT).show();
            isValid = false;
        }
        
        // Validate Date of Birth
        String dob = binding.dobEditText.getText().toString().trim();
        if (TextUtils.isEmpty(dob)) {
            binding.dobLayout.setError("Date of birth is required");
            isValid = false;
        } else {
            binding.dobLayout.setError(null);
        }
        
        // Validate Phone Number
        String phone = binding.phoneEditText.getText().toString().trim();
        if (TextUtils.isEmpty(phone)) {
            binding.phoneLayout.setError("Phone number is required");
            isValid = false;
        } else if (!phone.matches("^\\d{10}$")) {
            binding.phoneLayout.setError("Phone number must be 10 digits");
            isValid = false;
        } else {
            binding.phoneLayout.setError(null);
        }
        
        // Validate Email
        String email = binding.emailEditText.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            binding.emailLayout.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.setError("Invalid email format");
            isValid = false;
        } else {
            binding.emailLayout.setError(null);
        }
        
        // Validate Location
        if (selectedLocationIds.isEmpty()) {
            binding.locationLayout.setError("Please select at least one location");
            isValid = false;
        } else {
            binding.locationLayout.setError(null);
        }
        
        // Validate Password (only when creating a new user)
        if (editId == null) {
            String password = binding.passwordEditText.getText().toString().trim();
            if (TextUtils.isEmpty(password)) {
                binding.passwordLayout.setError("Password is required");
                isValid = false;
            } else if (password.length() < 6) {
                binding.passwordLayout.setError("Password must be at least 6 characters");
                isValid = false;
            } else {
                binding.passwordLayout.setError(null);
            }
        }
        
        return isValid;
    }

    private void showImageSourceOptions() {
        new AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                if (which == 0) {
                    // Camera option
                    launchCamera();
                } else {
                    // Gallery option
                    galleryLauncher.launch("image/*");
                }
            })
            .show();
    }

    private void launchCamera() {
        Intent intent = new Intent(this, FaceCaptureActivity.class);
        cameraLauncher.launch(intent);
    }

    private void saveUserToFirebase() {
        // Check if we already validated inputs
        if (!validateInputs()) {
            return;
        }
        
        // Disable buttons during operation
        binding.nextButton.setEnabled(false);
        binding.cancelButton.setEnabled(true);
        
        // Get values from form fields
        String sevarthId = binding.sevarthIdEditText.getText().toString().trim();
        String firstName = binding.firstNameEditText.getText().toString().trim();
        String lastName = binding.lastNameEditText.getText().toString().trim();
        String gender = binding.maleRadioButton.isChecked() ? "Male" : "Female";
        String dob = binding.dobEditText.getText().toString().trim();
        String phone = binding.phoneEditText.getText().toString().trim();
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        
        Map<String, Object> additionalData = new HashMap<>();
        
        // Add the face path if an image was captured
        if (capturedImagePath != null) {
            additionalData.put("facePath", capturedImagePath);
        } else if (selectedImageUri != null) {
            additionalData.put("facePath", selectedImageUri.toString());
        }
        
        // Log what we're doing
        Log.d(TAG, "Saving user data for " + (editId != null ? "editing" : "creating") + 
              " user with Sevarth ID: " + sevarthId);
        
        // If we're creating a new user, first create in Firebase Authentication
        if (editId == null && !password.isEmpty()) {
            // Use the email provided by the user, never auto-generate one
            if (email.isEmpty()) {
                binding.emailLayout.setError("Email is required for account creation");
                binding.nextButton.setEnabled(true);
                return;
            }
            
            // Create a final copy of the input data to be used in lambda
            final String emailFinal = email;
            final Map<String, Object> finalAdditionalData = new HashMap<>(additionalData);
            
            // IMPORTANT: Show a progress dialog to let the user know something is happening
            AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setMessage("Creating user account...")
                .setCancelable(false)
                .show();
            
            // Create the user in Firebase Authentication
            mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    // User created successfully in Authentication
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser != null) {
                        // Update display name
                        firebaseUser.updateProfile(new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                .setDisplayName(firstName + " " + lastName)
                                .build())
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "User display name updated successfully");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error updating user display name", e);
                                });
                        
                        // Set the UID in the additional data
                        finalAdditionalData.put("uid", firebaseUser.getUid());
                        
                        // Continue with user creation
                        proceedWithUserCreation(sevarthId, firstName, lastName, gender, dob, phone, emailFinal, finalAdditionalData);
                        
                        // Dismiss progress dialog
                        progressDialog.dismiss();
                    }
                })
                .addOnFailureListener(e -> {
                    // Dismiss progress dialog
                    progressDialog.dismiss();
                    
                    // Check if the error is because the user already exists
                    if (e.getMessage() != null && e.getMessage().contains("email address is already in use")) {
                        // The email is already registered, so we can try to sign in and get the UID
                        // Create final copies of the variables for use in the lambda
                        final String passwordFinal = password;
                        
                        mAuth.signInWithEmailAndPassword(emailFinal, passwordFinal)
                            .addOnSuccessListener(authResult -> {
                                FirebaseUser existingUser = authResult.getUser();
                                if (existingUser != null) {
                                    // Get the UID of the existing user
                                    finalAdditionalData.put("uid", existingUser.getUid());
                                    
                                    // Sign out from the existing user and continue
                                    mAuth.signOut();
                                    
                                    // Check if admin is still signed in
                                    if (mAuth.getCurrentUser() == null) {
                                        // Try to sign in as admin again using saved credentials
                                        String adminEmail = prefs.getString(KEY_EMAIL, "");
                                        String adminPassword = prefs.getString(KEY_PASSWORD, "");
                                        
                                        if (!adminEmail.isEmpty() && !adminPassword.isEmpty()) {
                                            mAuth.signInWithEmailAndPassword(adminEmail, adminPassword)
                                                .addOnSuccessListener(adminAuthResult -> {
                                                    // Admin signed in again, continue with user creation
                                                    proceedWithUserCreation(sevarthId, firstName, lastName, gender, dob, phone, emailFinal, finalAdditionalData);
                                                })
                                                .addOnFailureListener(adminAuthError -> {
                                                    // Failed to sign in as admin
                                                    binding.nextButton.setEnabled(true);
                                                    Toast.makeText(AddUserActivity.this, "Admin authentication error. Please log in again.", Toast.LENGTH_LONG).show();
                                                });
                                        } else {
                                            // No admin credentials saved
                                            binding.nextButton.setEnabled(true);
                                            Toast.makeText(AddUserActivity.this, "Admin authentication error. Please log in again.", Toast.LENGTH_LONG).show();
                                        }
                                    } else {
                                        // Admin is still signed in, continue with user creation
                                        proceedWithUserCreation(sevarthId, firstName, lastName, gender, dob, phone, emailFinal, finalAdditionalData);
                                    }
                                } else {
                                    // Couldn't get the existing user
                                    binding.nextButton.setEnabled(true);
                                    Toast.makeText(AddUserActivity.this, "Failed to retrieve existing user data.", Toast.LENGTH_LONG).show();
                                }
                            })
                            .addOnFailureListener(signInError -> {
                                // Failed to sign in with the existing email
                                binding.nextButton.setEnabled(true);
                                binding.cancelButton.setEnabled(true);
                                Toast.makeText(AddUserActivity.this, "Authentication error: " + signInError.getMessage(), Toast.LENGTH_LONG).show();
                            });
                    } else {
                        // Failed to create user in Authentication for other reasons
                        Log.e(TAG, "Error creating user in Firebase Authentication", e);
                        Toast.makeText(AddUserActivity.this, "Failed to create user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        binding.nextButton.setEnabled(true);
                        binding.cancelButton.setEnabled(true);
                    }
                });
        } else {
            // For editing, just proceed with the update
            proceedWithUserCreation(sevarthId, firstName, lastName, gender, dob, phone, email, additionalData);
        }
    }

    private void proceedWithUserCreation(String sevarthId, String firstName, String lastName, 
                                       String gender, String dob, String phone, String email, 
                                       Map<String, Object> additionalData) {
        // Create user data map
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", UUID.randomUUID().toString());
        userData.put("sevarthId", sevarthId);
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("gender", gender);
        userData.put("dateOfBirth", dob);
        userData.put("phoneNumber", phone);
        userData.put("email", email);
        userData.put("locationIds", new ArrayList<>(selectedLocationIds));
        userData.put("locationNames", new ArrayList<>(selectedLocationNames));
        userData.put("role", "user");
        userData.put("active", true);
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("updatedAt", System.currentTimeMillis());

        if (additionalData != null) {
            userData.putAll(additionalData);
        }

        // Get current admin's ID
        String adminId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (adminId != null) {
            userData.put("createdBy", adminId);
        }

        // If Firebase Auth UID is not set yet (common when adding through API), try to use the backend API
        if (!userData.containsKey("uid") && editId == null) {
            // Get API auth token
            String authToken = getAuthToken();
            
            if (authToken != null && !authToken.isEmpty()) {
                // Try to register via the API
                registerViaApi(sevarthId, firstName, lastName, gender, dob, phone, email, userData);
            } else {
                // No auth token available, try to continue with just Firestore
                Log.w(TAG, "No auth token available. User may not have proper Firebase Authentication.");
                
                // Try to still create the user, but the backend API may require authentication later
                processImagesAndSaveUser(userData, sevarthId);
            }
        } else {
            // Already have a UID or editing an existing user, proceed normally
            processImagesAndSaveUser(userData, sevarthId);
        }
    }

    private String getAuthToken() {
        // Get token from SharedPreferences
        return prefs.getString(KEY_AUTH_TOKEN, null);
    }

    private void registerViaApi(String sevarthId, String firstName, String lastName, 
                              String gender, String dob, String phone, String email,
                              Map<String, Object> userData) {
        
        // Get API Service from RetrofitClient
        com.example.mystartup.api.ApiService apiService = 
            com.example.mystartup.api.RetrofitClient.getInstance().getApiService();
        
        // Create request object
        com.example.mystartup.api.AdminRegistrationRequest request = 
            new com.example.mystartup.api.AdminRegistrationRequest(
                sevarthId, 
                firstName, 
                lastName, 
                gender, 
                dob, 
                phone, 
                email,
                selectedLocationIds.size() > 0 ? new ArrayList<>(selectedLocationIds).get(0) : "",
                selectedLocationNames.size() > 0 ? new ArrayList<>(selectedLocationNames).get(0) : "",
                binding.passwordEditText.getText().toString().trim()
            );
        
        // Get auth token for API call
        String authToken = "Bearer " + getAuthToken();
        
        // Call API
        apiService.registerUser(authToken, request)
            .enqueue(new retrofit2.Callback<com.example.mystartup.api.AdminRegistrationResponse>() {
                @Override
                public void onResponse(retrofit2.Call<com.example.mystartup.api.AdminRegistrationResponse> call, 
                                     retrofit2.Response<com.example.mystartup.api.AdminRegistrationResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if (response.body().isSuccess()) {
                            // Success! API created the user
                            Log.d(TAG, "User created via API: " + sevarthId);
                            
                            // Get the user ID and use it
                            String userId = response.body().getUserId();
                            if (userId != null && !userId.isEmpty()) {
                                userData.put("uid", userId);
                            }
                            
                            // Continue with image processing and saving to Firestore
                            processImagesAndSaveUser(userData, sevarthId);
                        } else {
                            // API reported an error
                            String errorMessage = response.body().getMessage();
                            Log.e(TAG, "API reported error: " + errorMessage);
                            Toast.makeText(AddUserActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                            binding.nextButton.setEnabled(true);
                            binding.cancelButton.setEnabled(true);
                        }
                    } else {
                        // HTTP error
                        try {
                            String errorBody = response.errorBody() != null ? 
                                response.errorBody().string() : "Unknown error";
                            Log.e(TAG, "API error: " + errorBody);
                            Toast.makeText(AddUserActivity.this, "API Error: " + errorBody, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing API error", e);
                        }
                        binding.nextButton.setEnabled(true);
                        binding.cancelButton.setEnabled(true);
                    }
                }
                
                @Override
                public void onFailure(retrofit2.Call<com.example.mystartup.api.AdminRegistrationResponse> call, Throwable t) {
                    // Network or other error
                    Log.e(TAG, "API call failed", t);
                    Toast.makeText(AddUserActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    
                    // Fall back to just saving to Firestore
                    Log.d(TAG, "Falling back to direct Firestore save without API");
                    processImagesAndSaveUser(userData, sevarthId);
                }
            });
    }

    // New helper method to organize the code better
    private void processImagesAndSaveUser(Map<String, Object> userData, String sevarthId) {
        // Handle different image sources
        if (capturedImagePath != null) {
            // Image from camera
            File imageFile = new File(capturedImagePath);
            if (imageFile.exists()) {
                Log.d(TAG, "Using camera captured image: " + capturedImagePath);
                uploadImageAndSaveUser(imageFile, sevarthId, userData);
            } else {
                Log.e(TAG, "Captured image file not found: " + capturedImagePath);
                saveUserToFirestore(userData, sevarthId);
            }
        } else if (selectedImageUri != null) {
            // Image from gallery
            try {
                Log.d(TAG, "Using gallery image: " + selectedImageUri);
                File tempFile = File.createTempFile("gallery_", ".jpg", getCacheDir());
                copyUriToFile(selectedImageUri, tempFile);
                uploadImageAndSaveUser(tempFile, sevarthId, userData);
            } catch (IOException e) {
                Log.e(TAG, "Error creating temp file for gallery image", e);
                saveUserToFirestore(userData, sevarthId);
            }
        } else {
            // No image, just save the user data
            Log.d(TAG, "No image provided, saving user data only");
            saveUserToFirestore(userData, sevarthId);
        }
    }

    private void uploadImageAndSaveUser(File imageFile, String sevarthId, Map<String, Object> userData) {
        Uri fileUri = Uri.fromFile(imageFile);
        
        // Create a filename using the Sevarth ID
        final String fileName = "face_" + sevarthId + ".jpg";
        StorageReference imageRef = storageRef.child("reference_images/" + fileName);
        
        // Show uploading message
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();
        
        // Set metadata for upload
        StorageMetadata metadata = new StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build();
        
        // Upload the file to Firebase Storage
        imageRef.putFile(fileUri, metadata)
            .addOnSuccessListener(taskSnapshot -> {
                // Get the download URL
                imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String imageUrl = uri.toString();
                    Log.d(TAG, "Image uploaded successfully: " + imageUrl);
                    
                    // Update userData with the Storage URL instead of local path
                    userData.put("faceImageUrl", imageUrl);
                    userData.remove("facePath");
                    
                    // Save user data to Firestore now that we have the image URL
                    saveUserToFirestore(userData, sevarthId);
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get download URL: " + e.getMessage());
                    // Continue with user creation even if getting URL fails
                    saveUserToFirestore(userData, sevarthId);
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to upload image: " + e.getMessage(), e);
                Toast.makeText(this, "Failed to upload image: " + e.getMessage(), Toast.LENGTH_LONG).show();
                
                // Try alternative storage location if reference_images fails
                if (e.getMessage() != null && e.getMessage().contains("permission")) {
                    uploadImageToRootFolder(imageFile, fileName, userData, sevarthId);
                } else {
                    // Continue with user creation even if image upload fails
                    saveUserToFirestore(userData, sevarthId);
                }
            });
    }

    private void uploadImageToRootFolder(File imageFile, String fileName, Map<String, Object> userData, String sevarthId) {
        Uri fileUri = Uri.fromFile(imageFile);
        StorageReference rootImageRef = storageRef.child(fileName);
        
        Toast.makeText(this, "Trying alternative upload location...", Toast.LENGTH_SHORT).show();
        
        rootImageRef.putFile(fileUri)
            .addOnSuccessListener(altTaskSnapshot -> {
                rootImageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String imageUrl = uri.toString();
                    Log.d(TAG, "Image uploaded successfully to root: " + imageUrl);
                    
                    // Update userData with the Storage URL instead of local path
                    userData.put("faceImageUrl", imageUrl);
                    userData.remove("facePath");
                    
                    // Save user data to Firestore
                    saveUserToFirestore(userData, sevarthId);
                }).addOnFailureListener(urlError -> {
                    // Continue without image
                    Log.e(TAG, "Failed to get download URL from root: " + urlError.getMessage());
                    saveUserToFirestore(userData, sevarthId);
                });
            })
            .addOnFailureListener(altError -> {
                Log.e(TAG, "Failed to upload image to root: " + altError.getMessage());
                // Continue without image as last resort
                saveUserToFirestore(userData, sevarthId);
            });
    }

    private void saveUserToFirestore(Map<String, Object> userData, String sevarthId) {
        // When editing, we might need to update certain fields only
        if (editId != null) {
            Log.d(TAG, "Updating existing user with Sevarth ID: " + sevarthId);
            
            // Don't overwrite createdAt timestamp for existing users
            userData.remove("createdAt");
            
            // Set updated timestamp to now
            userData.put("updatedAt", System.currentTimeMillis());
        } else {
            Log.d(TAG, "Creating new user with Sevarth ID: " + sevarthId);
            
            // Make sure we have the uid field set
            if (!userData.containsKey("uid")) {
                Log.w(TAG, "No UID found in user data. User might not be properly authenticated.");
            }
        }
    
        // Save to Firestore
        db.collection("users")
            .document(sevarthId)
            .set(userData)
            .addOnSuccessListener(aVoid -> {
                String message = editId != null ? "User updated successfully" : "User created successfully";
                
                // CRITICAL: Also save a duplicate document with UID as document ID
                // This ensures the user can be found by both sevarthId and UID
                if (userData.containsKey("uid")) {
                    String uid = (String) userData.get("uid");
                    if (uid != null && !uid.isEmpty()) {
                        // Save the same data with UID as the document ID
                        db.collection("users")
                            .document(uid)
                            .set(userData)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "User data also saved with UID as document ID: " + uid);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error saving user data with UID as document ID", e);
                            });
                    }
                }
                
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            })
            .addOnFailureListener(e -> {
                binding.nextButton.setEnabled(true);
                binding.cancelButton.setEnabled(true);
                Log.e(TAG, "Error " + (editId != null ? "updating" : "creating") + " user", e);
                Toast.makeText(this, "Failed to " + (editId != null ? "update" : "create") + " user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    // Helper method to copy content from a URI to a file
    private void copyUriToFile(Uri uri, File destinationFile) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("Failed to open input stream for URI: " + uri);
        }
        
        OutputStream outputStream = new FileOutputStream(destinationFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        outputStream.close();
        inputStream.close();
    }
} 