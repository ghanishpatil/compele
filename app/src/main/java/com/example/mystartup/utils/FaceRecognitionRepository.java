package com.example.mystartup.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.example.mystartup.api.AttendanceHistoryResponse;
import com.example.mystartup.api.AttendanceRequest;
import com.example.mystartup.api.AttendanceResponse;
import com.example.mystartup.api.FaceRecognitionApiService;
import com.example.mystartup.api.FaceRegistrationRequest;
import com.example.mystartup.api.FaceRegistrationResponse;
import com.example.mystartup.api.FaceVerificationRequest;
import com.example.mystartup.api.FaceVerificationResponse;
import com.example.mystartup.api.RetrofitClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository class for face recognition operations
 */
public class FaceRecognitionRepository {
    private static final String TAG = "FaceRecognitionRepo";
    private final FaceRecognitionApiService apiService;
    
    /**
     * Constructor
     */
    public FaceRecognitionRepository() {
        apiService = RetrofitClient.getInstance().getFaceRecognitionApiService();
    }
    
    /**
     * Register a user's face
     * 
     * @param sevarthId User's Sevarth ID
     * @param faceBitmap Face image bitmap
     * @param callback Callback for the result
     */
    public void registerFace(String sevarthId, Bitmap faceBitmap, RepositoryCallback<FaceRegistrationResponse> callback) {
        // Convert bitmap to base64
        String base64Image = bitmapToBase64(faceBitmap);
        if (base64Image == null) {
            callback.onError("Failed to convert image to base64");
            return;
        }
        
        // Create request
        FaceRegistrationRequest request = new FaceRegistrationRequest(sevarthId, base64Image);
        
        // Call API
        apiService.registerFace(request).enqueue(new Callback<FaceRegistrationResponse>() {
            @Override
            public void onResponse(Call<FaceRegistrationResponse> call, Response<FaceRegistrationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    try {
                        callback.onError(response.errorBody() != null 
                                ? response.errorBody().string() 
                                : "Unknown error");
                    } catch (IOException e) {
                        callback.onError("Failed to parse error response");
                    }
                }
            }
            
            @Override
            public void onFailure(Call<FaceRegistrationResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }
    
    /**
     * Verify a user's face
     * 
     * @param sevarthId User's Sevarth ID
     * @param faceBitmap Face image bitmap to verify
     * @param callback Callback for the result
     */
    public void verifyFace(String sevarthId, Bitmap faceBitmap, RepositoryCallback<FaceVerificationResponse> callback) {
        // Convert bitmap to base64
        String base64Image = bitmapToBase64(faceBitmap);
        if (base64Image == null) {
            callback.onError("Failed to convert image to base64");
            return;
        }
        
        // Create request
        FaceVerificationRequest request = new FaceVerificationRequest(sevarthId, base64Image);
        
        // Call API
        apiService.verifyFace(request).enqueue(new Callback<FaceVerificationResponse>() {
            @Override
            public void onResponse(Call<FaceVerificationResponse> call, Response<FaceVerificationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    try {
                        callback.onError(response.errorBody() != null 
                                ? response.errorBody().string() 
                                : "Unknown error");
                    } catch (IOException e) {
                        callback.onError("Failed to parse error response");
                    }
                }
            }
            
            @Override
            public void onFailure(Call<FaceVerificationResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }
    
    /**
     * Mark attendance
     * 
     * @param sevarthId User's Sevarth ID
     * @param attendanceType Either "check_in" or "check_out"
     * @param confidence Verification confidence (0-1)
     * @param locationId ID of the office location
     * @param userName User's name
     * @param userId User's Firebase UID
     * @param callback Callback for the result
     */
    public void markAttendance(String sevarthId, String attendanceType, float confidence, 
                             String locationId, String userName, String userId,
                             RepositoryCallback<AttendanceResponse> callback) {
        // Log request details for debugging
        Log.d(TAG, "Marking attendance with: sevarthId=" + sevarthId + 
                ", attendanceType=" + attendanceType + 
                ", confidence=" + confidence + 
                ", locationId=" + locationId + 
                ", userName=" + userName + 
                ", userId=" + userId);
        
        // Add fallback values if data is missing
        if (userName == null || userName.isEmpty()) {
            userName = "User"; // Default name if missing
            Log.w(TAG, "Using default user name because actual name is missing");
        }
        
        if (userId == null || userId.isEmpty()) {
            // Check if we can get the userId from Firebase Auth
            try {
                com.google.firebase.auth.FirebaseUser currentUser = 
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    userId = currentUser.getUid();
                    Log.d(TAG, "Retrieved user ID from Firebase Auth: " + userId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting current Firebase user: " + e.getMessage());
                // We'll continue with null userId, the server should handle it
            }
        }
        
        // Create request with complete user data
        AttendanceRequest request = new AttendanceRequest(sevarthId, attendanceType, confidence, 
                                                         locationId, userName, userId);
        
        // Call API
        apiService.markAttendance(request).enqueue(new Callback<AttendanceResponse>() {
            @Override
            public void onResponse(Call<AttendanceResponse> call, Response<AttendanceResponse> response) {
                // Log full response for debugging
                Log.d(TAG, "Server response code: " + response.code());
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        // Log the entire response object
                        Log.d(TAG, "Attendance response: " + response.body().toString());
                        
                        // Check if response indicates error even though HTTP code was success
                        if (response.body().isError()) {
                            Log.e(TAG, "Error response with success HTTP code: " + response.body().getMessage());
                            callback.onError(response.body().getMessage());
                        } else {
                            Log.d(TAG, "Attendance marked successfully: " + response.body().getMessage());
                            callback.onSuccess(response.body());
                        }
                    } else {
                        String errorJson = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        Log.e(TAG, "Error marking attendance: " + errorJson);
                        
                        // Try to extract a more user-friendly error message
                        String errorMessage = extractErrorMessage(errorJson);
                        callback.onError(errorMessage);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to parse error response", e);
                    callback.onError("Network error: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(Call<AttendanceResponse> call, Throwable t) {
                Log.e(TAG, "Network failure marking attendance", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Extract a user-friendly error message from the error JSON
     */
    private String extractErrorMessage(String errorJson) {
        if (errorJson == null || errorJson.isEmpty()) {
            return "Unknown error occurred";
        }
        
        try {
            // Don't show "User data incomplete" message anymore - handle this on the client side
            if (errorJson.contains("User data incomplete")) {
                return "Server error while processing your request. Please try again.";
            }
            
            // Don't propagate IP-related errors to the user
            if (errorJson.contains("IP") || errorJson.contains("ip address") || 
                errorJson.contains("network") || errorJson.contains("location")) {
                return "Server error. Please try again.";
            }
            
            // Try to parse as JSON and extract message
            if (errorJson.contains("message") && errorJson.contains("success")) {
                // Extract the message field from the JSON
                int messageStart = errorJson.indexOf("\"message\"");
                if (messageStart >= 0) {
                    int valueStart = errorJson.indexOf(":", messageStart) + 1;
                    int valueEnd = errorJson.indexOf(",", valueStart);
                    if (valueEnd < 0) {
                        valueEnd = errorJson.indexOf("}", valueStart);
                    }
                    if (valueStart >= 0 && valueEnd >= 0) {
                        String message = errorJson.substring(valueStart, valueEnd).trim();
                        // Remove quotes
                        if (message.startsWith("\"") && message.endsWith("\"")) {
                            message = message.substring(1, message.length() - 1);
                        }
                        return message;
                    }
                }
            }
            
            return "Server error: " + errorJson;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing error message", e);
            return "Error processing server response";
        }
    }
    
    /**
     * Mark attendance (backward compatibility method)
     * 
     * @param sevarthId User's Sevarth ID
     * @param attendanceType Either "check_in" or "check_out"
     * @param confidence Verification confidence (0-1)
     * @param locationId ID of the office location
     * @param callback Callback for the result
     */
    public void markAttendance(String sevarthId, String attendanceType, float confidence, 
                             String locationId, RepositoryCallback<AttendanceResponse> callback) {
        // Call the complete method with empty values for user name and ID
        markAttendance(sevarthId, attendanceType, confidence, locationId, "", "", callback);
    }
    
    /**
     * Get attendance history
     * 
     * @param sevarthId User's Sevarth ID
     * @param startDate Optional start date (YYYY-MM-DD)
     * @param endDate Optional end date (YYYY-MM-DD)
     * @param callback Callback for the result
     */
    public void getAttendanceHistory(String sevarthId, String startDate, String endDate,
                                   RepositoryCallback<AttendanceHistoryResponse> callback) {
        // Call API
        apiService.getAttendanceHistory(sevarthId, startDate, endDate)
                .enqueue(new Callback<AttendanceHistoryResponse>() {
            @Override
            public void onResponse(Call<AttendanceHistoryResponse> call, 
                                 Response<AttendanceHistoryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    try {
                        callback.onError(response.errorBody() != null 
                                ? response.errorBody().string() 
                                : "Unknown error");
                    } catch (IOException e) {
                        callback.onError("Failed to parse error response");
                    }
                }
            }
            
            @Override
            public void onFailure(Call<AttendanceHistoryResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }
    
    /**
     * Convert bitmap to base64 string
     * 
     * @param bitmap The bitmap to convert
     * @return The base64 string or null on failure
     */
    private String bitmapToBase64(Bitmap bitmap) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error converting bitmap to base64: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Mark attendance with a pre-configured request object
     * 
     * @param request The attendance request with all required fields
     * @param callback Callback for the result
     */
    public void markAttendanceWithRequest(AttendanceRequest request, RepositoryCallback<AttendanceResponse> callback) {
        if (request == null) {
            callback.onError("Request object cannot be null");
            return;
        }
        
        // Log request details for debugging
        Log.d(TAG, "Marking attendance with request: sevarthId=" + request.getSevarthId() + 
                ", attendanceType=" + request.getType() + 
                ", locationId=" + request.getLocationId() + 
                ", locationName=" + request.getLocationName());
        
        // Call API
        apiService.markAttendance(request).enqueue(new Callback<AttendanceResponse>() {
            @Override
            public void onResponse(Call<AttendanceResponse> call, Response<AttendanceResponse> response) {
                // Log full response for debugging
                Log.d(TAG, "Server response code: " + response.code());
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        // Log the entire response object
                        Log.d(TAG, "Attendance response: " + response.body().toString());
                        
                        // Check if response indicates error even though HTTP code was success
                        if (response.body().isError()) {
                            Log.e(TAG, "Error response with success HTTP code: " + response.body().getMessage());
                            callback.onError(response.body().getMessage());
                        } else {
                            Log.d(TAG, "Attendance marked successfully: " + response.body().getMessage());
                            callback.onSuccess(response.body());
                            
                            // Also save to local Firestore to ensure redundancy
                            saveToLocalFirestore(request);
                        }
                    } else {
                        String errorJson = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        Log.e(TAG, "Error marking attendance: " + errorJson);
                        
                        // Try to extract a more user-friendly error message
                        String errorMessage = extractErrorMessage(errorJson);
                        callback.onError(errorMessage);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to parse error response", e);
                    callback.onError("Network error: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(Call<AttendanceResponse> call, Throwable t) {
                Log.e(TAG, "Network failure marking attendance", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Helper method to save attendance to local Firestore as backup
     */
    private void saveToLocalFirestore(AttendanceRequest request) {
        try {
            // Create a simple Firestore repository if needed
            if (firestoreRepository == null) {
                firestoreRepository = new FirestoreAttendanceRepository();
            }
            
            // Save attendance record locally with location information
            firestoreRepository.saveAttendance(
                request.getSevarthId(),
                request.getUserId(),
                request.getUserName(),
                request.getType(),
                request.getVerificationConfidence(),
                request.getLocationId(),
                request.getLocationName(),
                new FirestoreAttendanceRepository.AttendanceCallback() {
                    @Override
                    public void onSuccess(String docId) {
                        Log.d(TAG, "Backup attendance record saved locally with ID: " + docId);
                    }
                    
                    @Override
                    public void onError(String errorMessage) {
                        Log.w(TAG, "Failed to save backup attendance record: " + errorMessage);
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error saving backup attendance record", e);
        }
    }
    
    // Keep a single Firestore repository instance
    private FirestoreAttendanceRepository firestoreRepository;
    
    /**
     * Repository callback interface
     */
    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }
} 