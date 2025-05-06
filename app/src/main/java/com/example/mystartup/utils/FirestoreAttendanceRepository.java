package com.example.mystartup.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Repository for saving attendance data to Firestore
 */
public class FirestoreAttendanceRepository {
    private static final String TAG = "FirestoreAttendanceRepo";
    private static final String COLLECTION_PATH = "face-recognition-attendance";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second delay between retries
    
    private final FirebaseFirestore db;
    private final Handler mainHandler;
    
    public FirestoreAttendanceRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Save attendance record to Firestore using sevarthId
     * @param sevarthId The Sevarth ID to use as document ID
     * @param userId The user's Firebase UID 
     * @param userName The user's display name
     * @param type The attendance type ("check_in" or "check_out")
     * @param verificationConfidence The verification confidence
     * @param locationId The office location ID
     * @param officeName The office location name
     * @param callback Callback for result
     */
    public void saveAttendance(String sevarthId, String userId, String userName, String type, 
                             double verificationConfidence, String locationId, String officeName,
                             AttendanceCallback callback) {
        if (sevarthId == null || sevarthId.isEmpty()) {
            String error = "saveAttendance: sevarthId is null or empty";
            Log.e(TAG, error);
            callback.onError(error);
            return;
        }

        try {
            // Generate document ID based on sevarthId and timestamp
            long timestamp = System.currentTimeMillis();
            String docId = sevarthId + "_" + timestamp;
            Log.d(TAG, "saveAttendance: Creating document with ID: " + docId);
            
            // Get current date and time
            Date now = new Date(timestamp);
            Timestamp firestoreTimestamp = new Timestamp(now);
            
            // Create date formatters with Indian Standard Time timezone
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
            
            // Set Indian Standard Time timezone (IST = UTC+5:30)
            TimeZone istTimeZone = TimeZone.getTimeZone("Asia/Kolkata");
            dateFormat.setTimeZone(istTimeZone);
            timeFormat.setTimeZone(istTimeZone);
            
            // Format current date and time in IST
            String formattedDate = dateFormat.format(now);
            String formattedTime = timeFormat.format(now);
            
            Log.d(TAG, "saveAttendance: Using device time in IST: " + now.toString() + 
                      ", Formatted as " + formattedDate + " " + formattedTime);
            
            // Create attendance data exactly matching backend structure
            Map<String, Object> attendanceData = new HashMap<>();
            attendanceData.put("sevarthId", sevarthId);
            attendanceData.put("userId", userId);
            attendanceData.put("userName", userName);
            attendanceData.put("type", type);
            attendanceData.put("timestamp", firestoreTimestamp);
            attendanceData.put("date", formattedDate);
            attendanceData.put("time", formattedTime);
            attendanceData.put("status", "Present");
            attendanceData.put("verificationConfidence", verificationConfidence);
            attendanceData.put("locationId", locationId);
            attendanceData.put("officeName", officeName);
            
            Log.d(TAG, "saveAttendance: Attempting to save attendance data: " + attendanceData);
            
            // First try with a direct set
            db.collection(COLLECTION_PATH)
                .document(docId)
                .set(attendanceData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "saveAttendance: Successfully saved attendance to Firestore with ID: " + docId);
                    mainHandler.post(() -> callback.onSuccess(docId));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "saveAttendance: Failed to save attendance with direct set: " + e.getMessage());
                    if (e instanceof FirebaseFirestoreException) {
                        retryWithBatch(docId, attendanceData, 0, callback);
                    } else {
                        mainHandler.post(() -> callback.onError(e.getMessage()));
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "saveAttendance: Unexpected error: " + e.getMessage(), e);
            mainHandler.post(() -> callback.onError(e.getMessage()));
        }
    }
    
    /**
     * Save attendance record to Firestore (legacy method for compatibility)
     */
    public void saveAttendance(String sevarthId, String userId, String userName, String type, 
                             double verificationConfidence, AttendanceCallback callback) {
        // Call the complete method with default values for location
        saveAttendance(sevarthId, userId, userName, type, verificationConfidence, 
                      "unknown", "Unknown Office", callback);
    }
    
    /**
     * Retry saving attendance with batch write
     * This is a fallback in case direct set fails due to Firestore contention
     */
    private void retryWithBatch(String docId, Map<String, Object> attendanceData, int attempt, AttendanceCallback callback) {
        if (attempt >= MAX_RETRIES) {
            Log.e(TAG, "retryWithBatch: Max retries (" + MAX_RETRIES + ") reached, giving up");
            mainHandler.post(() -> callback.onError("Failed to save after " + MAX_RETRIES + " attempts"));
            return;
        }
        
        // Add delay before retrying
        mainHandler.postDelayed(() -> {
            Log.d(TAG, "retryWithBatch: Retry #" + (attempt + 1) + " for document " + docId);
            
            // Try with batch write
            db.collection(COLLECTION_PATH)
                .document(docId)
                .set(attendanceData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "retryWithBatch: Successfully saved attendance with batch on attempt #" + (attempt + 1));
                    mainHandler.post(() -> callback.onSuccess(docId));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "retryWithBatch: Failed on attempt #" + (attempt + 1) + ": " + e.getMessage());
                    retryWithBatch(docId, attendanceData, attempt + 1, callback);
                });
        }, RETRY_DELAY_MS);
    }
    
    /**
     * Update the user document with the sevarthId
     */
    private void updateUserWithSevarthId(String userId, String sevarthId) {
        if (userId == null || userId.isEmpty() || sevarthId == null || sevarthId.isEmpty()) {
            Log.e(TAG, "updateUserWithSevarthId: userId or sevarthId is null or empty");
            return;
        }
        
        try {
            Map<String, Object> userData = new HashMap<>();
            userData.put("sevarthId", sevarthId);
            
            db.collection("users")
                .whereEqualTo("uid", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Get the user document and update it
                        String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("users").document(docId)
                            .set(userData, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "User document updated with sevarthId: " + sevarthId);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error updating user document: " + e.getMessage(), e);
                            });
                    } else {
                        Log.e(TAG, "No user document found for userId: " + userId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error querying for user document: " + e.getMessage(), e);
                });
        } catch (Exception e) {
            Log.e(TAG, "Exception in updateUserWithSevarthId: " + e.getMessage(), e);
        }
    }
    
    /**
     * Callback interface for async operations
     */
    public interface AttendanceCallback {
        void onSuccess(String docId);
        void onError(String errorMessage);
    }
} 