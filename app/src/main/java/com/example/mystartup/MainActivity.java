package com.example.mystartup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.mystartup.databinding.ActivityMainBinding;
import com.example.mystartup.fragments.OfficesFragment;
import com.example.mystartup.fragments.ReportsFragment;
import com.example.mystartup.fragments.UsersFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private static final String PREF_NAME = "AuthPrefs";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_SEVARTH_ID = "sevarth_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Set current date
        setCurrentDate();
        
        // Get user details and set greeting
        loadUserDetails();
        
        // Setup navigation
        setupNavigation();
        
        // Default to Users fragment
        loadFragment(new UsersFragment());

        // Log current user UID for debugging
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            Log.d("ADMIN_DEBUG", "Current user UID: " + uid);
            
            // Check if user exists in admins collection
            FirebaseFirestore.getInstance().collection("admins").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean isAdmin = documentSnapshot.exists();
                    Log.d("ADMIN_DEBUG", "Is admin in Firestore: " + isAdmin);
                    
                    if (!isAdmin) {
                        // Add this user as an admin if they aren't already
                        Map<String, Object> adminData = new HashMap<>();
                        adminData.put("uid", uid);
                        adminData.put("email", currentUser.getEmail());
                        adminData.put("role", "admin");
                        adminData.put("created_at", new Date());
                        
                        FirebaseFirestore.getInstance().collection("admins").document(uid)
                            .set(adminData)
                            .addOnSuccessListener(aVoid -> {
                                Log.d("ADMIN_DEBUG", "User added as admin successfully");
                                // Force token refresh
                                currentUser.getIdToken(true);
                            })
                            .addOnFailureListener(e -> {
                                Log.e("ADMIN_DEBUG", "Error adding admin", e);
                            });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ADMIN_DEBUG", "Error checking admin status", e);
                });
        }
    }

    private void setCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        binding.dateTextView.setText(currentDate);
    }

    private void loadUserDetails() {
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            String sevarthId = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_SEVARTH_ID, "");
            
            // First, check if we can get admin details from "users" collection using sevarthId
            if (!sevarthId.isEmpty()) {
                db.collection("users").document(sevarthId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String firstName = documentSnapshot.getString("firstName");
                            String lastName = documentSnapshot.getString("lastName");
                            
                            if (firstName != null && lastName != null) {
                                String greeting = "Good " + getTimeOfDay() + ", " + firstName + " " + lastName;
                                binding.greetingTextView.setText(greeting);
                                return; // Exit if we found the user
                            }
                        }
                        
                        // If we couldn't get from users collection, try the admins collection
                        tryGetAdminDetailsFromAdminsCollection(uid);
                    })
                    .addOnFailureListener(e -> {
                        // On failure, try the admins collection
                        tryGetAdminDetailsFromAdminsCollection(uid);
                    });
            } else {
                // If no sevarthId, try admins collection directly
                tryGetAdminDetailsFromAdminsCollection(uid);
            }
        }
    }
    
    private void tryGetAdminDetailsFromAdminsCollection(String uid) {
        Log.d("ADMIN_DEBUG", "Trying to get admin details from admins collection with UID: " + uid);
        
        // Look for admin document where authUid field matches current user's UID
        db.collection("admins")
            .whereEqualTo("authUid", uid)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    // Found document with matching authUid
                    DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                    String firstName = document.getString("firstName");
                    String lastName = document.getString("lastName");
                    
                    if (firstName != null && lastName != null) {
                        String greeting = "Good " + getTimeOfDay() + ", " + firstName + " " + lastName;
                        binding.greetingTextView.setText(greeting);
                        return;
                    }
                }
                
                // If we still couldn't find admin info, try to get it from the admin document using sevarthId
                tryGetAdminDetailsDirectly();
            })
            .addOnFailureListener(e -> {
                // If there was an error, try direct lookup
                tryGetAdminDetailsDirectly();
            });
    }

    private void tryGetAdminDetailsDirectly() {
        // As a final resort, try to look up all admins and see if there's a match with current user's email
        String currentEmail = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : null;
        
        if (currentEmail != null) {
            db.collection("admins")
                .whereEqualTo("email", currentEmail)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        
                        if (firstName != null && lastName != null) {
                            String greeting = "Good " + getTimeOfDay() + ", " + firstName + " " + lastName;
                            binding.greetingTextView.setText(greeting);
                        } else {
                            // If we have a document but no name, use the sevarthId or email
                            String sevarthId = document.getString("sevarthId");
                            String displayName = sevarthId != null ? sevarthId : currentEmail;
                            String greeting = "Good " + getTimeOfDay() + ", " + displayName;
                            binding.greetingTextView.setText(greeting);
                    }
                    } else if (currentEmail != null) {
                        // If all else fails, just use the email
                        String greeting = "Good " + getTimeOfDay() + ", " + currentEmail.split("@")[0];
                        binding.greetingTextView.setText(greeting);
                    }
                })
                .addOnFailureListener(e -> {
                    // If all lookups fail, just use a generic greeting or the email
                    if (mAuth.getCurrentUser() != null) {
                        String email = mAuth.getCurrentUser().getEmail();
                        String displayName = email != null ? email.split("@")[0] : "Admin";
                        String greeting = "Good " + getTimeOfDay() + ", " + displayName;
                        binding.greetingTextView.setText(greeting);
                    }
                });
        } else {
            // If we don't have an email, use display name from Firebase Auth
            if (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getDisplayName() != null) {
                String greeting = "Good " + getTimeOfDay() + ", " + mAuth.getCurrentUser().getDisplayName();
                binding.greetingTextView.setText(greeting);
            }
        }
    }
    
    private String getTimeOfDay() {
        int hour = new Date().getHours();
        if (hour < 12) {
            return "morning";
        } else if (hour < 17) {
            return "afternoon";
        } else {
            return "evening";
        }
    }
    
    private void setupNavigation() {
        binding.usersButton.setOnClickListener(v -> {
            selectTab(binding.usersButton);
            loadFragment(new UsersFragment());
        });
        
        binding.officesButton.setOnClickListener(v -> {
            selectTab(binding.officesButton);
            loadFragment(new OfficesFragment());
        });
        
        binding.reportsButton.setOnClickListener(v -> {
            selectTab(binding.reportsButton);
            loadFragment(new ReportsFragment());
        });
        
        binding.refreshButton.setOnClickListener(v -> {
            // Refresh current fragment
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (currentFragment != null) {
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.detach(currentFragment);
                transaction.attach(currentFragment);
                transaction.commit();
            }
        });
        
        binding.logoutButton.setOnClickListener(v -> {
            // Clear preferences and sign out
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
            
            FirebaseAuth.getInstance().signOut();
            
            // Return to login screen
            finish();
        });
        
        binding.settingsButton.setOnClickListener(v -> {
            // Open settings screen (to be implemented)
        });
    }
    
    private void selectTab(TextView selectedTab) {
        // Reset all tabs to default state
        binding.usersButton.setBackgroundResource(android.R.color.transparent);
        binding.officesButton.setBackgroundResource(android.R.color.transparent);
        binding.reportsButton.setBackgroundResource(android.R.color.transparent);
        
        // Highlight selected tab
        selectedTab.setBackgroundResource(R.drawable.selected_tab_bg);
    }
    
    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }

    @Override
    public void onBackPressed() {
        // Navigate back to the login screen instead of closing the app
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        
        FirebaseAuth.getInstance().signOut();
        
        // Return to login screen using Intent
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}