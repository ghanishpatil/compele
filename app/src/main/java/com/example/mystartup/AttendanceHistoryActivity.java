package com.example.mystartup;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mystartup.adapters.AttendanceAdapter;
import com.example.mystartup.databinding.ActivityAttendanceHistoryBinding;
import com.example.mystartup.models.AttendanceRecord;
import com.google.android.material.chip.Chip;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorListener;
import androidx.core.view.animation.PathInterpolatorCompat;
import android.view.animation.Animation;
import android.view.ViewGroup;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class AttendanceHistoryActivity extends AppCompatActivity {
    private static final String TAG = "AttendanceHistory";
    private ActivityAttendanceHistoryBinding binding;
    private AttendanceAdapter adapter;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration attendanceListener;
    private String currentFilter = "all"; // Default filter

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAttendanceHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Setup toolbar
        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        // Setup Bottom Navigation
        setupBottomNavigation();

        // Setup RecyclerView
        setupRecyclerView();

        // Setup filter chips
        setupFilterChips();

        // Load attendance records
        loadAttendanceRecords();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        
        // Set attendance as selected
        bottomNav.setSelectedItemId(R.id.nav_attendance);
        
        // Apply animations
        Animation navAnimation = AnimationUtils.loadAnimation(this, R.anim.bottom_nav_item_anim);
        
        // Handle navigation clicks
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            // Apply animation to the selected item
            View view = bottomNav.findViewById(itemId);
            if (view != null) {
                view.startAnimation(navAnimation);
            }
            
            if (itemId == R.id.nav_home) {
                // Navigate to home/dashboard
                Intent intent = new Intent(this, UserAttendanceActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
                return true;
            } 
            else if (itemId == R.id.nav_attendance) {
                // We're already on attendance history
                return true;
            }
            
            return false;
        });
    }

    private void setupRecyclerView() {
        adapter = new AttendanceAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.attendanceRecyclerView.setLayoutManager(layoutManager);
        binding.attendanceRecyclerView.setAdapter(adapter);
        
        // Add layout animation
        binding.attendanceRecyclerView.setLayoutAnimation(
                AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down));
                
        // Add item decoration for better spacing
        int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.recycler_item_spacing);
        binding.attendanceRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, 
                                     @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                if (position != parent.getAdapter().getItemCount() - 1) {
                    outRect.bottom = spacingInPixels;
                }
            }
        });
    }

    private void setupFilterChips() {
        // Apply animations to chips
        binding.chipAll.setOnClickListener(v -> {
            animateChip(binding.chipAll);
            currentFilter = "all";
            adapter.filterByType(currentFilter);
            binding.attendanceRecyclerView.scheduleLayoutAnimation();
        });
        
        binding.chipCheckIn.setOnClickListener(v -> {
            animateChip(binding.chipCheckIn);
            currentFilter = "check_in";
            adapter.filterByType(currentFilter);
            binding.attendanceRecyclerView.scheduleLayoutAnimation();
        });
        
        binding.chipCheckOut.setOnClickListener(v -> {
            animateChip(binding.chipCheckOut);
            currentFilter = "check_out";
            adapter.filterByType(currentFilter);
            binding.attendanceRecyclerView.scheduleLayoutAnimation();
        });
    }
    
    private void animateChip(Chip chip) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(chip, "scaleX", 0.8f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(chip, "scaleY", 0.8f, 1.0f);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY);
        animatorSet.setDuration(150);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }

    private void loadAttendanceRecords() {
        showLoading(true);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No current user found");
            showError("User not authenticated");
            return;
        }
        
        final String currentUserId = currentUser.getUid();
        final String userEmail = currentUser.getEmail();
        
        // Log the current user details for debugging
        Log.d(TAG, "Current user - UID: " + currentUserId + ", Email: " + userEmail);
        
        // Get the sevarthId from SharedPreferences first - this is more reliable
        String sevarthIdFromPrefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .getString("sevarth_id", null);
                
        if (sevarthIdFromPrefs != null && !sevarthIdFromPrefs.isEmpty()) {
            Log.d(TAG, "Using sevarthId from SharedPreferences: " + sevarthIdFromPrefs);
            setupAttendanceListener(sevarthIdFromPrefs, currentUserId);
            return;
        }
        
        // If user has email, extract probable sevarthId
        String probableSevarthId = null;
        if (userEmail != null && userEmail.contains("@")) {
            probableSevarthId = userEmail.substring(0, userEmail.indexOf('@'));
            Log.d(TAG, "Extracted probable sevarthId from email: " + probableSevarthId);
            
            // Save to shared preferences for future use
            getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("sevarth_id", probableSevarthId)
                .apply();
                
            // Use this probable sevarthId directly to query attendance
            if (probableSevarthId != null && !probableSevarthId.isEmpty()) {
                setupAttendanceListener(probableSevarthId, currentUserId);
                return;
            }
        }

        // Try to find user document
        Log.d(TAG, "Querying users collection for user with UID: " + currentUserId);
        
        db.collection("users")
            .whereEqualTo("uid", currentUserId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                Log.d(TAG, "User query by UID returned " + queryDocumentSnapshots.size() + " documents");
                
                if (!queryDocumentSnapshots.isEmpty()) {
                    // Get sevarthId from user document
                    String sevarthId = queryDocumentSnapshots.getDocuments()
                            .get(0).getString("sevarthId");
                    
                    Log.d(TAG, "Retrieved sevarthId from user document: " + sevarthId);

                    if (sevarthId != null && !sevarthId.isEmpty()) {
                        // Save to shared preferences for future use
                        getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("sevarth_id", sevarthId)
                            .apply();
                            
                        // Set up real-time listener for this user's attendance records
                        setupAttendanceListener(sevarthId, currentUserId);
                    } else {
                        Log.e(TAG, "sevarthId in user document is null or empty");
                        // Try direct query instead of showing error
                        tryDirectQuery();
                    }
                } else {
                    // Try direct query
                    tryDirectQuery();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to query user: " + e.getMessage());
                tryDirectQuery();
            });
    }
    
    private void setupAttendanceListener(String sevarthId, String currentUserId) {
        Log.d(TAG, "Setting up real-time listener for sevarthId: " + sevarthId);
        
        try {
            // Create a query that checks both fields
            Log.d(TAG, "Querying 'face-recognition-attendance' collection for sevarthId: " + sevarthId);
            
            // Clear any existing listener
            if (attendanceListener != null) {
                attendanceListener.remove();
                attendanceListener = null;
            }
            
            // Show something is happening
            showLoading(true);
            
            // Try both ways of querying to be more tolerant across devices 
            // First try the sevarthId query
            db.collection("face-recognition-attendance")
                .whereEqualTo("sevarthId", sevarthId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    int count = snapshots.size();
                    Log.d(TAG, "Query by sevarthId returned " + count + " records");
                    
                    if (count > 0) {
                        // We found records - process them
                        processAttendanceSnapshots(snapshots);
                    } else {
                        // If no records found by sevarthId, try with userId
                        tryBackupQuery(currentUserId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Query by sevarthId failed: " + e.getMessage());
                    // Try the backup query
                    tryBackupQuery(currentUserId);
                });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up attendance listener", e);
            showError("Error: " + e.getMessage());
            showLoading(false);
            showEmptyView(true);
        }
    }
    
    private void processAttendanceSnapshots(com.google.firebase.firestore.QuerySnapshot snapshots) {
        List<AttendanceRecord> records = new ArrayList<>();
        
        for (QueryDocumentSnapshot document : snapshots) {
            try {
                Log.d(TAG, "Processing document: " + document.getId());
                AttendanceRecord record = document.toObject(AttendanceRecord.class);
                record.setId(document.getId());
                
                // Add default values for missing fields to improve compatibility
                if (record.getDate() == null) {
                    // Try to extract date from timestamp
                    com.google.firebase.Timestamp timestamp = document.getTimestamp("timestamp");
                    if (timestamp != null) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                        record.setDate(dateFormat.format(timestamp.toDate()));
                    } else {
                        record.setDate("Unknown Date");
                    }
                }
                
                if (record.getTime() == null) {
                    // Try to extract time from timestamp
                    com.google.firebase.Timestamp timestamp = document.getTimestamp("timestamp");
                    if (timestamp != null) {
                        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
                        timeFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                        record.setTime(timeFormat.format(timestamp.toDate()));
                    } else {
                        record.setTime("00:00:00");
                    }
                }
                
                if (record.getType() == null) {
                    // Default to "Unknown" if type is missing
                    record.setType("Unknown");
                }
                
                // Log the record details
                Log.d(TAG, "Record details: date=" + record.getDate() + 
                           ", time=" + record.getTime() + 
                           ", type=" + record.getType() + 
                           ", office=" + record.getOfficeName());
                
                records.add(record);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing document: " + document.getId(), e);
            }
        }
        
        // Update UI on main thread
        runOnUiThread(() -> {
            adapter.setRecords(records);
            showLoading(false);
            showEmptyView(records.isEmpty());
            
            if (!records.isEmpty()) {
                binding.attendanceRecyclerView.scheduleLayoutAnimation();
            }
        });
    }
    
    // Try a backup query using userId if sevarthId query returns no results
    private void tryBackupQuery(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "Cannot try backup query - userId is null or empty");
            showEmptyView(true);
            showLoading(false);
            return;
        }
        
        Log.d(TAG, "Trying backup query with userId: " + userId);
        
        db.collection("face-recognition-attendance")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snapshots -> {
                int count = snapshots.size();
                Log.d(TAG, "Backup query returned " + count + " records");
                
                if (count == 0) {
                    // If no records found with userId either, try a more basic query
                    tryDirectQuery();
                    return;
                }
                
                List<AttendanceRecord> records = new ArrayList<>();
                
                for (QueryDocumentSnapshot document : snapshots) {
                    try {
                        AttendanceRecord record = document.toObject(AttendanceRecord.class);
                        record.setId(document.getId());
                        records.add(record);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing document in backup query: " + document.getId(), e);
                    }
                }
                
                runOnUiThread(() -> {
                    adapter.setRecords(records);
                    showLoading(false);
                    showEmptyView(records.isEmpty());
                    
                    if (!records.isEmpty()) {
                        binding.attendanceRecyclerView.scheduleLayoutAnimation();
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Backup query failed: " + e.getMessage(), e);
                // If backup query fails, try a direct query as last resort
                tryDirectQuery();
            });
    }
    
    // Last resort - try a direct query without where clause
    private void tryDirectQuery() {
        Log.d(TAG, "Trying direct query as last resort");
        
        // Remove the error message
        runOnUiThread(() -> {
            showLoading(true);
            // Hide any error toast that might be showing
            Toast.makeText(this, "Searching for attendance records...", Toast.LENGTH_SHORT).show();
        });
        
        // Get the most recent 100 records and filter client-side
        db.collection("face-recognition-attendance")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener(snapshots -> {
                int count = snapshots.size();
                Log.d(TAG, "Direct query returned " + count + " total records");
                
                if (count == 0) {
                    runOnUiThread(() -> {
                        showEmptyView(true);
                        showLoading(false);
                        Toast.makeText(this, "No attendance records found in the system", Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                
                // Try to get current user
                FirebaseUser currentUser = mAuth.getCurrentUser();
                String userEmail = currentUser != null ? currentUser.getEmail() : null;
                String userUid = currentUser != null ? currentUser.getUid() : null;
                
                Log.d(TAG, "Filtering records for user - UID: " + userUid + ", Email: " + userEmail);
                
                // Parse sevarthId from email if available
                String probableSevarthId = null;
                if (userEmail != null && userEmail.contains("@")) {
                    probableSevarthId = userEmail.substring(0, userEmail.indexOf('@'));
                    Log.d(TAG, "Extracted probable sevarthId from email for filtering: " + probableSevarthId);
                }
                
                final String finalProbableSevarthId = probableSevarthId;
                final String finalUserUid = userUid;
                
                List<AttendanceRecord> records = new ArrayList<>();
                
                for (QueryDocumentSnapshot document : snapshots) {
                    try {
                        // Try to client-side filter for current user's records
                        String docSevarthId = document.getString("sevarthId");
                        String docUserId = document.getString("userId");
                        
                        // Log document details for debugging
                        Log.d(TAG, "Examining record: ID=" + document.getId() + 
                              ", sevarthId=" + docSevarthId + 
                              ", userId=" + docUserId);
                        
                        // Match by sevarthId or userId
                        boolean matches = false;
                        
                        if (finalProbableSevarthId != null && finalProbableSevarthId.equals(docSevarthId)) {
                            matches = true;
                            Log.d(TAG, "Record matched by sevarthId");
                        } else if (finalUserUid != null && finalUserUid.equals(docUserId)) {
                            matches = true;
                            Log.d(TAG, "Record matched by userId");
                        }
                        
                        if (matches) {
                            AttendanceRecord record = document.toObject(AttendanceRecord.class);
                            record.setId(document.getId());
                            
                            // Add even if we couldn't map to an object
                            if (record != null) {
                                records.add(record);
                                Log.d(TAG, "Added matching record: " + document.getId());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing document in direct query: " + document.getId(), e);
                    }
                }
                
                runOnUiThread(() -> {
                    adapter.setRecords(records);
                    showLoading(false);
                    showEmptyView(records.isEmpty());
                    
                    if (!records.isEmpty()) {
                        binding.attendanceRecyclerView.scheduleLayoutAnimation();
                    } else {
                        // Show a more helpful message
                        Toast.makeText(AttendanceHistoryActivity.this, 
                            "No attendance records found for your account", Toast.LENGTH_LONG).show();
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Direct query failed: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    showEmptyView(true);
                    showLoading(false);
                    showError("Could not load attendance records");
                });
            });
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.progressBar.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        } else {
            binding.progressBar.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
        }
    }

    private void showEmptyView(boolean show) {
        binding.emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.attendanceRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        
        if (show) {
            binding.emptyView.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
            
            // Hide any error toast that's showing
            // Replace any error shown at the bottom of the screen with the empty view text
            View toastText = findViewById(android.R.id.message);
            if (toastText != null && toastText.getParent() != null && toastText.getParent() instanceof ViewGroup) {
                ((ViewGroup) toastText.getParent()).removeView(toastText);
            }
        }
    }
    
    private void showError(String message) {
        if (message == null || message.isEmpty()) return;
        
        // Don't show "User not found" errors - we already handle this
        if (message.contains("User") && message.contains("not found")) {
            return;
        }
        
        // Only show the toast if we have a meaningful error
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            navigateToHome();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
        navigateToHome();
    }
    
    private void navigateToHome() {
        Intent intent = new Intent(this, UserAttendanceActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove the listener when the activity is destroyed
        if (attendanceListener != null) {
            attendanceListener.remove();
        }
    }
} 