package com.example.mystartup.api;

import com.google.gson.annotations.SerializedName;

/**
 * Response model for face registration
 */
public class FaceRegistrationResponse {
    @SerializedName("message")
    private String message;
    
    @SerializedName("face_image_url")
    private String faceImageUrl;
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getFaceImageUrl() {
        return faceImageUrl;
    }
    
    public void setFaceImageUrl(String faceImageUrl) {
        this.faceImageUrl = faceImageUrl;
    }
} 