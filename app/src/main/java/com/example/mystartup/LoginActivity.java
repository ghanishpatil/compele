package com.example.mystartup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import android.util.Log;
import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mystartup.api.ApiService;
import com.example.mystartup.api.RetrofitClient;
import com.example.mystartup.api.LoginRequest;
import com.example.mystartup.api.LoginResponse;
import com.example.mystartup.databinding.ActivityLoginBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import org.json.JSONObject;

import java.net.UnknownHostException;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;
    private ApiService apiService;
    private static final Pattern SEVARTH_ID_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}$");
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private static final String PREF_NAME = "AuthPrefs";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password"; // Note: In production, use more secure storage
    private static final String KEY_SEVARTH_ID = "sevarth_id";
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Verify Firebase connectivity and security rules
        verifyFirebaseAccess();

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // Check if user is already logged in
        // We need to validate both the saved token and Firebase auth state
        String savedToken = prefs.getString(KEY_AUTH_TOKEN, null);
        if (savedToken != null && mAuth.getCurrentUser() != null) {
            // Verify that the user is actually an admin
            String role = prefs.getString(KEY_USER_ROLE, "");
            if (role.equals("admin")) {
                navigateToMain();
                finish();
                return;
            } else {
                // Clear any invalid session state
                clearAuthState();
            }
        } else {
            // Clear any stale tokens
            clearAuthState();
        }

        setupRetrofit();
        setupClickListeners();
        setupTextWatchers();
        setupRoleSelection();
    }

    private void setupRetrofit() {
        apiService = RetrofitClient.getInstance().getApiService();
    }

    private void setupClickListeners() {
        binding.loginButton.setOnClickListener(v -> attemptLogin());
        binding.forgotPasswordTextView.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });
    }

    private void setupTextWatchers() {
        binding.sevarthIdEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateSevarthId(s.toString());
            }
        });
    }

    private void setupRoleSelection() {
        binding.roleRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            // Admin registration removed
        });
    }

    private boolean validateSevarthId(String sevarthId) {
        if (sevarthId.isEmpty()) {
            showSevarthIdError("Sevarth ID is required");
            return false;
        }
        
        if (!SEVARTH_ID_PATTERN.matcher(sevarthId).matches()) {
            showSevarthIdError("Sevarth ID must contain both letters and numbers (minimum 6 characters)");
            return false;
        }

        hideSevarthIdError();
        return true;
    }

    private void showSevarthIdError(String error) {
        binding.sevarthIdErrorText.setText(error);
        binding.sevarthIdErrorText.setVisibility(View.VISIBLE);
    }

    private void hideSevarthIdError() {
        binding.sevarthIdErrorText.setVisibility(View.GONE);
    }

    private void attemptLogin() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        String sevarthId = binding.sevarthIdEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        String role = binding.userRadioButton.isChecked() ? "user" : "admin";

        if (!validateSevarthId(sevarthId)) {
            return;
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Format email for Firebase Authentication
        String email = sevarthId + "@example.com";

        binding.loginButton.setEnabled(false);
        
        // Show progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setMessage("Logging in...")
            .setCancelable(false)
            .create();
        progressDialog.show();
        
        // Instead of trying backend API first, verify credentials in Firestore or local prefs
        tryDirectFirebaseAuth(email, password, role, sevarthId, progressDialog, false);
    }
    
    private void tryDirectFirebaseAuth(String email, String password, String role, String sevarthId, 
                                        AlertDialog progressDialog, boolean showPasswordError) {
        // Don't try Firebase Auth if we already determined the password is wrong
        if (showPasswordError) {
            progressDialog.dismiss();
            Toast.makeText(LoginActivity.this, 
                "Invalid password. Please try again or reset your password.", 
                Toast.LENGTH_LONG).show();
            return;
        }

        binding.loginButton.setEnabled(false);
        Log.d("LoginActivity", "Checking credentials for sevarth ID: " + sevarthId);
        
        // Check if we have a password override for this user
        SharedPreferences passwordOverrides = getSharedPreferences("PasswordOverrides", Context.MODE_PRIVATE);
        
        // Check by sevarthId first
        String overridePassword = passwordOverrides.getString(sevarthId, null);
        
        if (overridePassword != null) {
            // We have a saved password - check if the entered password matches
            if (overridePassword.equals(password)) {
                // Correct password provided
                Log.d("LoginActivity", "Password matched override from local storage by sevarthId");
                
                // Fetch user data from Firestore to ensure we have all needed info
                fetchUserDataAndLogin(sevarthId, role, "local_override", progressDialog);
            } else {
                // Incorrect password
                progressDialog.dismiss();
                binding.loginButton.setEnabled(true);
                Log.d("LoginActivity", "Password did NOT match override from local storage");
                Toast.makeText(LoginActivity.this, "Invalid password. Please try again.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        
        // Now we'll check if there's a phone number saved in the SharedPreferences
        // Loop through all keys to find phone number formats
        boolean phoneNumberFound = false;
        String matchedPhoneNumber = null;
        String savedPhonePassword = null;
        
        Map<String, ?> allPrefs = passwordOverrides.getAll();
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            String key = entry.getKey();
            // Check if key looks like a phone number (starts with + and has digits)
            if (key.startsWith("+") && key.matches("\\+[0-9]+")) {
                phoneNumberFound = true;
                matchedPhoneNumber = key;
                savedPhonePassword = (String) entry.getValue();
                if (savedPhonePassword != null && savedPhonePassword.equals(password)) {
                    // We found a match by phone number with correct password
                    Log.d("LoginActivity", "Password matched override from local storage by phone number: " + key);
                    
                    // Create a final copy for the lambda to use
                    final String phoneNumber = matchedPhoneNumber;
                    
                    // Search for user with this phone number in Firestore
                    db.collection("users")
                        .whereEqualTo("phoneNumber", phoneNumber)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            if (!querySnapshot.isEmpty()) {
                                // Found user with this phone number
                                String userId = querySnapshot.getDocuments().get(0).getId();
                                fetchUserDataAndLogin(userId, role, "phone_override", progressDialog);
                            } else {
                                // No user found with this phone number in Firestore
                                // Create a generic login with just the phone info
                        progressDialog.dismiss();
                                binding.loginButton.setEnabled(true);
                                
                                // Create a placeholder token
                                String placeholderToken = "phone_override_" + System.currentTimeMillis();
                                
                                // Save auth details - use the phone number as sevarthId as fallback
                                saveAuthToken(placeholderToken, role, phoneNumber);
                                
                                Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                                navigateToMain();
                            }
                        })
                        .addOnFailureListener(e -> {
                            // Failed to query Firestore for phone number
                            Log.e("LoginActivity", "Failed to query user by phone: " + e.getMessage());
                            // Still allow login with just the phone info
                            progressDialog.dismiss();
                            binding.loginButton.setEnabled(true);
                            
                            // Create a placeholder token
                            String placeholderToken = "phone_override_" + System.currentTimeMillis();
                            
                            // Save auth details
                            saveAuthToken(placeholderToken, role, phoneNumber);
                            
                            Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                            navigateToMain();
                        });
                    return;
                }
            }
        }
        
        // If we found a phone number but password didn't match, show invalid password
        if (phoneNumberFound) {
            progressDialog.dismiss();
            binding.loginButton.setEnabled(true);
            Log.d("LoginActivity", "Found phone number but password did not match");
            Toast.makeText(LoginActivity.this, "Invalid password. Please try again.", Toast.LENGTH_LONG).show();
            return;
        }
        
        // If we get here, we didn't find a match in local overrides, continue with Firestore check
        // First check if the user exists in Firestore and validate password
        db.collection("users")
            .document(sevarthId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String storedPassword = documentSnapshot.getString("password");
                    boolean isPasswordCorrect = storedPassword != null && storedPassword.equals(password);
                    
                    if (isPasswordCorrect) {
                        // Password matches in Firestore, allow login
                        Log.d("LoginActivity", "Password verified in Firestore, login successful");
                        fetchUserDataAndLogin(sevarthId, role, "firestore_verified", progressDialog);
                    } else {
                        // Password doesn't match Firestore record
                        progressDialog.dismiss();
                        binding.loginButton.setEnabled(true);
                        Log.e("LoginActivity", "Password mismatch with Firestore record");
                        Toast.makeText(LoginActivity.this, "Invalid password. Please try again.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    // As a last resort, try Firebase Auth
                    // User doesn't exist in Firestore by sevarthId or local prefs
                    mAuth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {
                            progressDialog.dismiss();
                            binding.loginButton.setEnabled(true);
                            
                            Log.d("LoginActivity", "Firebase Auth successful");
                            
                            // Create a placeholder token
                            String placeholderToken = "firebase_auth_" + System.currentTimeMillis();
                            
                            // Save auth details
                            saveAuthToken(placeholderToken, role);
                            
                            Toast.makeText(LoginActivity.this, "Login successful via Firebase authentication!", Toast.LENGTH_SHORT).show();
                            navigateToMain();
                        })
                        .addOnFailureListener(e -> {
                            progressDialog.dismiss();
                            binding.loginButton.setEnabled(true);
                            
                            Log.e("LoginActivity", "Authentication failed: " + e.getMessage(), e);
                            
                            // Handle specific auth errors and provide better feedback
                            String errorMessage = "Login failed: Invalid username or password";
                            if (e.getMessage() != null) {
                                String errorCode = e.getMessage();
                                Log.d("LoginActivity", "Error code: " + errorCode);
                                
                                if (errorCode.contains("PERMISSION_DENIED")) {
                                    errorMessage = "Firebase permission denied. Please check your internet connection and security rules.";
                                } else if (errorCode.contains("ERROR_USER_NOT_FOUND") || errorCode.contains("user-not-found")) {
                                    errorMessage = "Account not found. Please check your Sevarth ID.";
                                } else if (errorCode.contains("ERROR_WRONG_PASSWORD") || errorCode.contains("wrong-password")) {
                                    errorMessage = "Incorrect password. Please try again.";
                                } else if (errorCode.contains("ERROR_TOO_MANY_REQUESTS") || errorCode.contains("too-many-requests")) {
                                    errorMessage = "Too many failed attempts. Please try again later.";
                                } else if (errorCode.contains("ERROR_NETWORK") || errorCode.contains("network-request-failed")) {
                                    errorMessage = "Network error. Please check your internet connection.";
                                }
                            }
                            
                            // Generic login failure
                            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        });
                }
            })
            .addOnFailureListener(e -> {
                // Error querying Firestore
                Log.e("LoginActivity", "Error querying Firestore: " + e.getMessage());
                progressDialog.dismiss();
                binding.loginButton.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    /**
     * Fetch user data from Firestore and proceed with login
     */
    private void fetchUserDataAndLogin(String userId, String role, String authMethod, AlertDialog progressDialog) {
        Log.d("LoginActivity", "Attempting to fetch user data from Firestore for userId: " + userId);
        
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                progressDialog.dismiss();
                binding.loginButton.setEnabled(true);
                
                // Create a placeholder token
                String placeholderToken = authMethod + "_" + System.currentTimeMillis();
                
                if (documentSnapshot.exists()) {
                    // We found the user, save their data for app use
                    String userName = documentSnapshot.getString("name");
                    String userEmail = documentSnapshot.getString("email");
                    String userPhone = documentSnapshot.getString("phoneNumber");
                    
                    Log.d("LoginActivity", "User document found with name: " + 
                        (userName != null ? userName : "null") + 
                        ", email: " + (userEmail != null ? userEmail : "null"));
                    
                    // Save auth details with user data included
                    saveUserDataAndToken(placeholderToken, role, userId, userName, userEmail, userPhone);
                    
                    Log.d("LoginActivity", "User data loaded successfully for: " + userId);
                } else {
                    // User document doesn't exist, use available data
                    Log.d("LoginActivity", "No user document found for: " + userId);
                    saveAuthToken(placeholderToken, role);
                }
                
                Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                navigateToMain();
            })
            .addOnFailureListener(e -> {
                progressDialog.dismiss();
                binding.loginButton.setEnabled(true);
                
                Log.e("LoginActivity", "Failed to fetch user data: " + e.getMessage(), e);
                
                // Check specifically for permission issues
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.contains("PERMISSION_DENIED")) {
                    Log.e("LoginActivity", "Firestore permission denied - check security rules");
                    Toast.makeText(LoginActivity.this, 
                        "User data access denied. Please contact an administrator.", 
                        Toast.LENGTH_LONG).show();
                    
                    // Fall back to Firebase auth login mode
                    String email = userId + "@example.com";
                    String password = binding.passwordEditText.getText().toString().trim();
                    
                    // Attempt to login with just Firebase Auth as a fallback
                    mAuth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {
                            // Still allow login with minimal data
                            String placeholderToken = "firebase_auth_fallback_" + System.currentTimeMillis();
                            saveAuthToken(placeholderToken, role);
                            
                            Toast.makeText(LoginActivity.this, "Login successful via Firebase!", Toast.LENGTH_SHORT).show();
                            navigateToMain();
                        })
                        .addOnFailureListener(authError -> {
                            // If even Firebase Auth fails, we have to give up
                            Log.e("LoginActivity", "Firebase Auth fallback failed: " + authError.getMessage(), authError);
                            Toast.makeText(LoginActivity.this, 
                                "Login failed. Please try again or contact support.", 
                                Toast.LENGTH_LONG).show();
                        });
                } else {
                    // For other errors, still try to proceed with login
                    String placeholderToken = authMethod + "_" + System.currentTimeMillis();
                    saveAuthToken(placeholderToken, role);
                    
                    Toast.makeText(LoginActivity.this, 
                        "Login successful, but user data couldn't be retrieved.", 
                        Toast.LENGTH_SHORT).show();
                    navigateToMain();
                }
            });
    }
    
    private void saveUserDataAndToken(String token, String role, String userId, String name, String email, String phone) {
        String sevarthId = binding.sevarthIdEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        
        // Use email from user data if available, otherwise construct one
        if (email == null || email.isEmpty()) {
            email = sevarthId + "@example.com";
        }
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_AUTH_TOKEN, token);
        editor.putString(KEY_USER_ROLE, role);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_PASSWORD, password);
        editor.putString(KEY_SEVARTH_ID, sevarthId);
        
        // Store additional user data
        editor.putString("user_id", userId);
        editor.putString("user_name", name != null ? name : "");
        editor.putString("user_phone", phone != null ? phone : "");
        
        editor.apply();
        
        Log.d("LoginActivity", "User data and auth token saved to preferences");
    }

    private void saveAuthToken(String token, String role) {
        String sevarthId = binding.sevarthIdEditText.getText().toString().trim();
        saveAuthToken(token, role, sevarthId);
    }
    
    private void saveAuthToken(String token, String role, String sevarthId) {
        String email = sevarthId + "@example.com";
        String password = binding.passwordEditText.getText().toString().trim();
        
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_USER_ROLE, role)
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_SEVARTH_ID, sevarthId)
            .apply();
            
        Log.d("LoginActivity", "Auth token and user data saved to preferences");
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }

    private void navigateToMain() {
        // Get the saved role
        String role = prefs.getString(KEY_USER_ROLE, "");
        
        // If user is an admin, navigate to admin dashboard (MainActivity)
        if (role.equals("admin")) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } 
        // If user is a regular user, navigate to location selection
        else if (role.equals("user")) {
            Intent intent = new Intent(this, LocationSelectionActivity.class);
            startActivity(intent);
        }
        // Default fallback
        else {
            Toast.makeText(this, "Unknown role: " + role, Toast.LENGTH_SHORT).show();
        }
        
        finish();
    }

    private void handleApiError(Response<?> response) {
        if (response.errorBody() != null) {
            try {
                String errorBody = response.errorBody().string();
                JSONObject errorJson = new JSONObject(errorBody);
                String errorMessage = errorJson.optString("message", "Invalid credentials");
                String detailedLog = "Error code: " + response.code() + ", Message: " + errorMessage;
                Log.e("LoginActivity", detailedLog);
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e("LoginActivity", "Error parsing response: " + e.getMessage());
                Toast.makeText(this, "An error occurred. Please try again.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e("LoginActivity", "Error body is null, code: " + response.code());
            Toast.makeText(this, "Server error. Please try again later.", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper method to clear auth state
    private void clearAuthState() {
        prefs.edit().clear().apply();
        mAuth.signOut();
    }

    // Add this new method to verify Firebase access
    private void verifyFirebaseAccess() {
        // Test read access to Firestore
        db.collection("users")
           .limit(1)
           .get()
           .addOnSuccessListener(querySnapshot -> {
               Log.d("LoginActivity", "Firebase Firestore access test successful");
           })
           .addOnFailureListener(e -> {
               Log.e("LoginActivity", "Firebase Firestore access test failed: " + e.getMessage(), e);
               if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
                   // This is just a diagnostic message. We'll still let the user try to log in.
                   Log.e("LoginActivity", "Firebase security rules are preventing access. Check your rules settings.");
               }
           });
        
        // Test Firebase Auth configuration
        mAuth.signOut(); // Clear any existing session
        Log.d("LoginActivity", "Firebase Auth initialized and ready");
    }
} 