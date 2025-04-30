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
        // Disable buttons to prevent double submission
        binding.nextButton.setEnabled(false);
        binding.cancelButton.setEnabled(false);
        
        // Get the values from form
        String sevarthId = binding.sevarthIdEditText.getText().toString().trim();
        String firstName = binding.firstNameEditText.getText().toString().trim();
        String lastName = binding.lastNameEditText.getText().toString().trim();
        String gender = binding.maleRadioButton.isChecked() ? "Male" : "Female";
        String dob = binding.dobEditText.getText().toString().trim();
        String phone = binding.phoneEditText.getText().toString().trim();
        String email = binding.emailEditText.getText().toString().trim();
        String password = editId == null ? binding.passwordEditText.getText().toString().trim() : "";
        
        // Add the image path to the user data
        Map<String, Object> additionalData = new HashMap<>();
        if (capturedImagePath != null) {
            additionalData.put("facePath", capturedImagePath);
        }
        
        // Log what we're doing
        Log.d(TAG, "Saving user data for " + (editId != null ? "editing" : "creating") + 
              " user with Sevarth ID: " + sevarthId);
        
        proceedWithUserCreation(sevarthId, firstName, lastName, gender, dob, phone, email, additionalData);
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

        // Handle different image sources
        if (selectedImageUri != null) {
            // User selected an image from gallery
            try {
                File imageFile = createFileFromUri(selectedImageUri, sevarthId);
                if (imageFile != null && imageFile.exists()) {
                    uploadImageAndSaveUser(imageFile, sevarthId, userData);
                } else {
                    Toast.makeText(this, "Failed to process selected image", Toast.LENGTH_SHORT).show();
                    saveUserToFirestore(userData, sevarthId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing selected image: " + e.getMessage(), e);
                Toast.makeText(this, "Error processing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                saveUserToFirestore(userData, sevarthId);
            }
        } else if (additionalData.containsKey("facePath") && additionalData.get("facePath") != null) {
            // We have a captured face image
            String localPath = (String) additionalData.get("facePath");
            File imageFile = new File(localPath);
            if (imageFile.exists()) {
                uploadImageAndSaveUser(imageFile, sevarthId, userData);
            } else {
                // No image or image file doesn't exist, just save the user data
                saveUserToFirestore(userData, sevarthId);
            }
        } else {
            // No image to upload, just save the user
            saveUserToFirestore(userData, sevarthId);
        }
    }
    
    private File createFileFromUri(Uri uri, String sevarthId) throws IOException {
        // Create a directory for reference images if it doesn't exist
        File mediaDir = new File(getExternalFilesDir(null), "reference_images");
        if (!mediaDir.exists()) {
            mediaDir.mkdirs();
        }
        
        // Create a file with the proper naming convention
        File destinationFile = new File(mediaDir, "face_" + sevarthId + ".jpg");
        
        // Copy the content from the URI to the file
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            return null;
        }
        
        OutputStream outputStream = new FileOutputStream(destinationFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        outputStream.close();
        inputStream.close();
        
        return destinationFile;
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
        }
    
        // Save to Firestore
        db.collection("users")
            .document(sevarthId)
            .set(userData)
            .addOnSuccessListener(aVoid -> {
                String message = editId != null ? "User updated successfully" : "User created successfully";
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
} 