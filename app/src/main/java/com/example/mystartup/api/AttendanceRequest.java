package com.example.mystartup.api;

import com.google.gson.annotations.SerializedName;

/**
 * Request model for marking attendance
 */
public class AttendanceRequest {
    @SerializedName("sevarth_id")
    private String sevarthId;
    
    @SerializedName("type")
    private String type;
    
    @SerializedName("verification_confidence")
    private float verificationConfidence;
    
    public AttendanceRequest(String sevarthId, String type, float verificationConfidence) {
        this.sevarthId = sevarthId;
        this.type = type;
        this.verificationConfidence = verificationConfidence;
    }
    
    public String getSevarthId() {
        return sevarthId;
    }
    
    public void setSevarthId(String sevarthId) {
        this.sevarthId = sevarthId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public float getVerificationConfidence() {
        return verificationConfidence;
    }
    
    public void setVerificationConfidence(float verificationConfidence) {
        this.verificationConfidence = verificationConfidence;
    }
} 