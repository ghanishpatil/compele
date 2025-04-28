package com.example.mystartup.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * API interface for face recognition and attendance-related endpoints
 */
public interface FaceRecognitionApiService {
    
    /**
     * Register a user's face
     * @param request Face registration request with sevarth_id and face_image as base64
     * @return Registration response
     */
    @POST("api/register-face")
    Call<FaceRegistrationResponse> registerFace(@Body FaceRegistrationRequest request);
    
    /**
     * Verify a user's face
     * @param request Face verification request with sevarth_id and face_image as base64
     * @return Verification response
     */
    @POST("api/verify-face")
    Call<FaceVerificationResponse> verifyFace(@Body FaceVerificationRequest request);
    
    /**
     * Mark attendance
     * @param request Attendance request with sevarth_id, type, and verification_confidence
     * @return Attendance response
     */
    @POST("api/mark-attendance")
    Call<AttendanceResponse> markAttendance(@Body AttendanceRequest request);
    
    /**
     * Get attendance history for a user
     * @param sevarthId User's Sevarth ID
     * @param startDate Optional start date (YYYY-MM-DD)
     * @param endDate Optional end date (YYYY-MM-DD)
     * @return Attendance history response
     */
    @GET("api/attendance-history")
    Call<AttendanceHistoryResponse> getAttendanceHistory(
            @Query("sevarth_id") String sevarthId,
            @Query("start_date") String startDate,
            @Query("end_date") String endDate);
    
    /**
     * Health check endpoint
     * @return Health status
     */
    @GET("health")
    Call<ResponseBody> healthCheck();
} 