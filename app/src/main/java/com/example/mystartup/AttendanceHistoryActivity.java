package com.example.mystartup;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mystartup.adapters.AttendanceAdapter;
import com.example.mystartup.databinding.ActivityAttendanceHistoryBinding;
import com.example.mystartup.models.AttendanceRecord;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

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

        // Setup RecyclerView
        setupRecyclerView();

        // Setup filter chips
        setupFilterChips();

        // Load attendance records
        loadAttendanceRecords();
    }

    private void setupRecyclerView() {
        adapter = new AttendanceAdapter(this);
        binding.attendanceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.attendanceRecyclerView.setAdapter(adapter);
    }

    private void setupFilterChips() {
        binding.chipAll.setOnClickListener(v -> {
            currentFilter = "all";
            adapter.filterByType(currentFilter);
        });
        
        binding.chipCheckIn.setOnClickListener(v -> {
            currentFilter = "check_in";
            adapter.filterByType(currentFilter);
        });
        
        binding.chipCheckOut.setOnClickListener(v -> {
            currentFilter = "check_out";
            adapter.filterByType(currentFilter);
        });
    }

    private void loadAttendanceRecords() {
        showLoading(true);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No current user found");
            showError("User not authenticated");
            return;
        }
        
        String currentUserId = currentUser.getUid();
        Log.d(TAG, "Loading attendance records for user ID: " + currentUserId);

        // First get the user document to retrieve sevarthId
        db.collection("users")
            .whereEqualTo("uid", currentUser.getUid())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                Log.d(TAG, "User query returned " + queryDocumentSnapshots.size() + " documents");
                
                if (!queryDocumentSnapshots.isEmpty()) {
                    // Get sevarthId from user document
                    String sevarthId = queryDocumentSnapshots.getDocuments()
                            .get(0).getString("sevarthId");
                    
                    Log.d(TAG, "Retrieved sevarthId: " + sevarthId);

                    if (sevarthId != null && !sevarthId.isEmpty()) {
                        // Set up real-time listener for this user's attendance records
                        setupAttendanceListener(sevarthId, currentUserId);
                    } else {
                        Log.e(TAG, "sevarthId is null or empty");
                        showLoading(false);
                        showEmptyView(true);
                        showError("Sevarth ID not found");
                    }
                } else {
                    Log.e(TAG, "No user document found for UID: " + currentUser.getUid());
                    showLoading(false);
                    showEmptyView(true);
                    showError("User details not found");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching user details: " + e.getMessage());
                showLoading(false);
                showError("Error loading user details: " + e.getMessage());
            });
    }
    
    private void setupAttendanceListener(String sevarthId, String currentUserId) {
        Log.d(TAG, "Setting up real-time listener for sevarthId: " + sevarthId);
        
        try {
            // Create a simple query without ordering to avoid index issues
            Log.d(TAG, "Querying 'face-recognition-attendance' collection for sevarthId: " + sevarthId);
            
            // Query based on sevarthId instead of document ID
            Query query = db.collection("face-recognition-attendance")
                    .whereEqualTo("sevarthId", sevarthId);
                    
            // Set up the real-time listener
            attendanceListener = query.addSnapshotListener((snapshots, e) -> {
                if (e != null) {
                    Log.e(TAG, "Listen failed: " + e.getMessage(), e);
                    showError("Failed to listen for attendance updates: " + e.getMessage());
                    showLoading(false);
                    return;
                }

                if (snapshots == null) {
                    Log.e(TAG, "Snapshot is null");
                    showEmptyView(true);
                    showLoading(false);
                    return;
                }
                
                if (snapshots.isEmpty()) {
                    Log.d(TAG, "No attendance records found for sevarthId: " + sevarthId);
                    showEmptyView(true);
                    showLoading(false);
                    return;
                }
                
                Log.d(TAG, "Attendance data updated, document count: " + snapshots.size());
                List<AttendanceRecord> records = new ArrayList<>();
                
                // Process all documents
                for (QueryDocumentSnapshot document : snapshots) {
                    try {
                        Log.d(TAG, "Processing document: " + document.getId() + " with data: " + document.getData());
                        
                        // Create attendance record from document
                        AttendanceRecord record = new AttendanceRecord();
                        record.setId(document.getId());
                        record.setDate(document.getString("date"));
                        record.setStatus(document.getString("status"));
                        record.setTime(document.getString("time"));
                        record.setTimestamp(document.getTimestamp("timestamp"));
                        record.setType(document.getString("type"));
                        record.setUserId(document.getString("userId"));
                        record.setUserName(document.getString("userName"));
                        
                        // Explicitly get sevarthId from the document
                        String docSevarthId = document.getString("sevarthId");
                        record.setSevarthId(docSevarthId);
                        
                        // Get the office name if it exists
                        String officeName = document.getString("officeName");
                        record.setOfficeName(officeName);
                        
                        // Try to get verification confidence if it exists
                        if (document.contains("verificationConfidence")) {
                            Double confidence = document.getDouble("verificationConfidence");
                            if (confidence != null) {
                                record.setVerificationConfidence(confidence);
                            }
                        }
                        
                        records.add(record);
                        Log.d(TAG, "Added attendance record: " + document.getId() + ", sevarthId: " + docSevarthId);
                    } catch (Exception ex) {
                        Log.e(TAG, "Error parsing document " + document.getId() + ": " + ex.getMessage(), ex);
                    }
                }
                
                // First group by sevarthId, then sort by timestamp (most recent first)
                // Since we're already filtering by sevarthId in the query, this should give records for one user
                // But this ensures correct sorting if multiple sevarthIds are somehow present
                records.sort((r1, r2) -> {
                    // First compare by sevarthId
                    int sevarthCompare = 0;
                    if (r1.getSevarthId() != null && r2.getSevarthId() != null) {
                        sevarthCompare = r1.getSevarthId().compareTo(r2.getSevarthId());
                    }
                    
                    // If sevarthIds are the same, compare by timestamp (newest first)
                    if (sevarthCompare == 0) {
                        if (r1.getTimestamp() == null || r2.getTimestamp() == null) {
                            return 0;
                        }
                        return r2.getTimestamp().compareTo(r1.getTimestamp());
                    }
                    
                    return sevarthCompare;
                });
                
                // Update adapter with found records and apply current filter
                Log.d(TAG, "Updating adapter with " + records.size() + " records, filter: " + currentFilter);
                adapter.setRecords(records);
                adapter.filterByType(currentFilter);
                
                // Show empty view if no records
                showEmptyView(records.isEmpty());
                showLoading(false);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up attendance listener: " + e.getMessage(), e);
            showError("Error setting up attendance updates: " + e.getMessage());
            showLoading(false);
        }
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showEmptyView(boolean show) {
        binding.attendanceRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
        // Navigate back to UserAttendanceActivity instead of just finishing
        Intent intent = new Intent(this, UserAttendanceActivity.class);
        startActivity(intent);
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