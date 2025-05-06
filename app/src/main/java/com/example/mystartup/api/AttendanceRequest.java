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
    
    @SerializedName("location_id")
    private String locationId;
    
    @SerializedName("user_name")
    private String userName;
    
    @SerializedName("uid")
    private String userId;
    
    @SerializedName("location_name")
    private String locationName;
    
    // Simple constructor for backward compatibility
    public AttendanceRequest(String sevarthId, String type, float verificationConfidence, String locationId) {
        this.sevarthId = sevarthId;
        this.type = type;
        this.verificationConfidence = verificationConfidence;
        this.locationId = locationId;
    }
    
    // Complete constructor with all fields
    public AttendanceRequest(String sevarthId, String type, float verificationConfidence, 
                            String locationId, String userName, String userId) {
        this.sevarthId = sevarthId;
        this.type = type;
        this.verificationConfidence = verificationConfidence;
        this.locationId = locationId;
        this.userName = userName;
        this.userId = userId;
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
    
    public String getLocationId() {
        return locationId;
    }
    
    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
} 