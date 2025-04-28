package com.example.mystartup.api;

import com.google.gson.annotations.SerializedName;

/**
 * Response model for face verification
 */
public class FaceVerificationResponse {
    @SerializedName("verified")
    private boolean verified;
    
    @SerializedName("confidence")
    private float confidence;
    
    @SerializedName("message")
    private String message;
    
    public boolean isVerified() {
        return verified;
    }
    
    public void setVerified(boolean verified) {
        this.verified = verified;
    }
    
    public float getConfidence() {
        return confidence;
    }
    
    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
} 