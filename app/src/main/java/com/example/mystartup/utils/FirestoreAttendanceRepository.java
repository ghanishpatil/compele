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
     * @param callback Callback for result
     */
    public void saveAttendance(String sevarthId, String userId, String userName, String type, 
                             double verificationConfidence, AttendanceCallback callback) {
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
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
            
            // Create attendance data exactly matching backend structure
            Map<String, Object> attendanceData = new HashMap<>();
            attendanceData.put("sevarthId", sevarthId);
            attendanceData.put("userId", userId);
            attendanceData.put("userName", userName);
            attendanceData.put("type", type);
            attendanceData.put("timestamp", firestoreTimestamp);
            attendanceData.put("date", dateFormat.format(now));
            attendanceData.put("time", timeFormat.format(now));
            attendanceData.put("status", "Present");
            attendanceData.put("verificationConfidence", verificationConfidence);
            
            Log.d(TAG, "saveAttendance: Attempting to save attendance data: " + attendanceData);
            
            // First try with a direct set
            saveWithRetry(docId, attendanceData, callback, dateFormat.format(now), timeFormat.format(now), 0);
            
        } catch (Exception e) {
            String errorMsg = "Exception during attendance save preparation: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            callback.onError(errorMsg);
        }
    }
    
    private void saveWithRetry(String docId, Map<String, Object> attendanceData, 
                             AttendanceCallback callback, String date, String time, int retryCount) {
        if (retryCount >= MAX_RETRIES) {
            String error = "Maximum retry attempts reached. All attempts to save attendance failed.";
            Log.e(TAG, error);
            callback.onError(error);
            return;
        }

        Log.d(TAG, "saveWithRetry: Attempt " + (retryCount + 1) + " for document: " + docId);
        
        db.collection(COLLECTION_PATH)
            .document(docId)
            .set(attendanceData)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "saveWithRetry: Successfully saved document with ID: " + docId);
                callback.onSuccess(docId, date, time);
            })
            .addOnFailureListener(e -> {
                String errorMsg = "Attempt " + (retryCount + 1) + " failed: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                
                // Check specific error types
                if (e instanceof FirebaseFirestoreException) {
                    FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
                    Log.e(TAG, "Firestore error code: " + firestoreException.getCode());
                    
                    // Handle specific error cases
                    switch (firestoreException.getCode()) {
                        case PERMISSION_DENIED:
                            callback.onError("Permission denied. Please check authentication.");
                            return;
                        case UNAVAILABLE:
                            // Retry after delay for network issues
                            retryAfterDelay(docId, attendanceData, callback, date, time, retryCount);
                            return;
                        case ALREADY_EXISTS:
                            // Generate new document ID and retry
                            String newDocId = docId + "_retry_" + System.currentTimeMillis();
                            Log.d(TAG, "Document already exists, retrying with new ID: " + newDocId);
                            saveWithRetry(newDocId, attendanceData, callback, date, time, retryCount);
                            return;
                        default:
                            // For other errors, try regular retry
                            retryAfterDelay(docId, attendanceData, callback, date, time, retryCount);
                    }
                } else {
                    // For non-Firestore exceptions, try regular retry
                    retryAfterDelay(docId, attendanceData, callback, date, time, retryCount);
                }
            });
    }
    
    private void retryAfterDelay(String docId, Map<String, Object> attendanceData,
                                AttendanceCallback callback, String date, String time, int retryCount) {
        mainHandler.postDelayed(() -> {
            Log.d(TAG, "retryAfterDelay: Retrying after delay, attempt " + (retryCount + 2));
            saveWithRetry(docId, attendanceData, callback, date, time, retryCount + 1);
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
     * Callback interface for attendance operations
     */
    public interface AttendanceCallback {
        void onSuccess(String documentId, String date, String time);
        void onError(String errorMessage);
    }
} 