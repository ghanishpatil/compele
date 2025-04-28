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
            
            db.collection("admins")
                .whereEqualTo("uid", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                        String firstName = document.getString("first_name");
                        String lastName = document.getString("last_name");
                        
                        if (firstName != null && lastName != null) {
                            String greeting = "Good " + getTimeOfDay() + ", " + firstName + " " + lastName;
                            binding.greetingTextView.setText(greeting);
                        }
                    }
                });
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