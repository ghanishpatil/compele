package com.example.mystartup.api;

import com.google.gson.annotations.SerializedName;

/**
 * Response model for registering a user through admin API
 */
public class AdminRegistrationResponse {
    @SerializedName("message")
    private String message;
    
    @SerializedName("success")
    private boolean success;
    
    @SerializedName("user_id")
    private String userId;
    
    public AdminRegistrationResponse() {
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
} 