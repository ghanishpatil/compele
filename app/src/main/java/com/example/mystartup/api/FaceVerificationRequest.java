package com.example.mystartup.api;

import com.google.gson.annotations.SerializedName;

/**
 * Request model for face verification
 */
public class FaceVerificationRequest {
    @SerializedName("sevarth_id")
    private String sevarthId;
    
    @SerializedName("face_image")
    private String faceImage;
    
    public FaceVerificationRequest(String sevarthId, String faceImage) {
        this.sevarthId = sevarthId;
        this.faceImage = faceImage;
    }
    
    public String getSevarthId() {
        return sevarthId;
    }
    
    public void setSevarthId(String sevarthId) {
        this.sevarthId = sevarthId;
    }
    
    public String getFaceImage() {
        return faceImage;
    }
    
    public void setFaceImage(String faceImage) {
        this.faceImage = faceImage;
    }
} 