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
        binding.forgotPasswordTextView.setOnClickListener(v -> 
            Toast.makeText(this, "Forgot password functionality coming soon!", Toast.LENGTH_SHORT).show()
        );
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

        binding.loginButton.setEnabled(false);
        
        // Add debug toast to show what URL we're connecting to
        Toast.makeText(this, "Connecting to: " + RetrofitClient.getInstance().getBaseUrl(), 
            Toast.LENGTH_LONG).show();
        Log.d("LoginActivity", "Attempting login with backend at: " + 
            RetrofitClient.getInstance().getBaseUrl());
        
        LoginRequest request = new LoginRequest(sevarthId, password, role);
        apiService.login(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                binding.loginButton.setEnabled(true);
                
                // Add more detailed logging
                Log.d("LoginActivity", "Login response received. Success: " + response.isSuccessful() 
                    + ", Code: " + response.code());
                
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();
                    
                    // Log token details
                    Log.d("LoginActivity", "Received token: " + 
                        (loginResponse.getToken() != null ? "Valid token" : "Null token"));
                    
                    if (loginResponse.getToken() == null) {
                        Log.e("LoginActivity", "Server returned success but with a null token!");
                        Toast.makeText(LoginActivity.this, 
                            "Authentication error: Server returned a null token", 
                            Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    // Sign in with Firebase using the custom token
                    mAuth.signInWithCustomToken(loginResponse.getToken())
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d("LoginActivity", "Firebase auth successful");
                                saveAuthToken(loginResponse.getToken(), role);
                                Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                                navigateToMain();
                            } else {
                                Log.e("LoginActivity", "Firebase auth failed: " + 
                                    (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                                Toast.makeText(LoginActivity.this, 
                                    "Authentication failed: " + 
                                    (task.getException() != null ? task.getException().getMessage() : "Unknown error"), 
                                    Toast.LENGTH_LONG).show();
                            }
                        });
                } else {
                    handleApiError(response);
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                binding.loginButton.setEnabled(true);
                String errorMessage = t instanceof UnknownHostException ? 
                    "Cannot connect to server. Please check your internet connection." :
                    "Network error: " + t.getMessage();
                Log.e("LoginActivity", "Login request failed: " + t.getMessage(), t);
                Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
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

    private void saveAuthToken(String token, String role) {
        String sevarthId = binding.sevarthIdEditText.getText().toString().trim();
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
} 