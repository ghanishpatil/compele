package com.example.mystartup.utils;

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
    
    private final FirebaseFirestore db;
    
    public FirestoreAttendanceRepository() {
        this.db = FirebaseFirestore.getInstance();
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
            Log.e(TAG, "saveAttendance: sevarthId is null or empty");
            callback.onError("Sevarth ID is required");
            return;
        }
        
        try {
            // Generate document ID based on sevarthId and timestamp
            // This ensures document IDs are unique but also contain the sevarthId
            String docId = sevarthId + "_" + System.currentTimeMillis();
            Log.d(TAG, "saveAttendance: Creating document with ID: " + docId);
            
            // Get current date and time
            Date now = new Date();
            Timestamp timestamp = new Timestamp(now);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
            
            // Create attendance data
            Map<String, Object> attendanceData = new HashMap<>();
            // Ensure sevarthId is explicitly saved in the document
            attendanceData.put("sevarthId", sevarthId);
            attendanceData.put("userId", userId);
            attendanceData.put("userName", userName);
            attendanceData.put("type", type);
            attendanceData.put("timestamp", timestamp);
            attendanceData.put("date", dateFormat.format(now));
            attendanceData.put("time", timeFormat.format(now));
            attendanceData.put("status", "Present");
            attendanceData.put("verificationConfidence", verificationConfidence);
            
            Log.d(TAG, "saveAttendance: Attempting to save attendance data: " + attendanceData.toString());
            
            // Also update the user's document with their sevarthId
            updateUserWithSevarthId(userId, sevarthId);
            
            // Save to Firestore with SetOptions.merge() to avoid overwrites
            db.collection(COLLECTION_PATH)
                .document(docId)
                .set(attendanceData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "saveAttendance: Successfully saved document with ID: " + docId);
                    callback.onSuccess(docId, dateFormat.format(now), timeFormat.format(now));
                })
                .addOnFailureListener(e -> {
                    String errorMsg = "Failed to save attendance: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                    Log.e(TAG, "saveAttendance: " + errorMsg, e);
                    callback.onError(errorMsg);
                    
                    // Try an alternative approach if the first one fails
                    retryWithTransaction(docId, attendanceData, callback, dateFormat.format(now), timeFormat.format(now));
                });
        } catch (Exception e) {
            String errorMsg = "Exception during attendance save: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            callback.onError(errorMsg);
        }
    }
    
    /**
     * Retry saving attendance with a transaction if the regular save fails
     */
    private void retryWithTransaction(String docId, Map<String, Object> attendanceData, 
                                    AttendanceCallback callback, String date, String time) {
        Log.d(TAG, "retryWithTransaction: Attempting to save using transaction for document ID: " + docId);
        
        try {
            db.runTransaction(transaction -> {
                transaction.set(db.collection(COLLECTION_PATH).document(docId), attendanceData);
                return null;
            }).addOnSuccessListener(aVoid -> {
                Log.d(TAG, "retryWithTransaction: Successfully saved document with transaction: " + docId);
                callback.onSuccess(docId, date, time);
            }).addOnFailureListener(e -> {
                String errorMsg = "Failed to save with transaction: " + e.getMessage();
                Log.e(TAG, "retryWithTransaction: " + errorMsg, e);
                callback.onError(errorMsg);
            });
        } catch (Exception e) {
            String errorMsg = "Exception during transaction: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            callback.onError(errorMsg);
        }
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