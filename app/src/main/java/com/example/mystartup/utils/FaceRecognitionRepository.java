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
     * @param callback Callback for the result
     */
    public void markAttendance(String sevarthId, String attendanceType, float confidence, 
                             RepositoryCallback<AttendanceResponse> callback) {
        // Create request
        AttendanceRequest request = new AttendanceRequest(sevarthId, attendanceType, confidence);
        
        // Call API
        apiService.markAttendance(request).enqueue(new Callback<AttendanceResponse>() {
            @Override
            public void onResponse(Call<AttendanceResponse> call, Response<AttendanceResponse> response) {
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
            public void onFailure(Call<AttendanceResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
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
     * Repository callback interface
     */
    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }
} 