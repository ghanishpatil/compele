package com.example.mystartup.models;

import com.google.firebase.Timestamp;
import java.util.Date;

public class AttendanceRecord {
    private String id;
    // Fields matching exactly what's in Firebase
    private String date;
    private String status;
    private String time;
    private Timestamp timestamp;
    private String type;
    private String userId;
    private String userName;
    private double verificationConfidence;
    
    // Additional fields that might be present in other records
    private String sevarthId;
    private String locationId;
    private String officeName;
    private String locationName; // Some devices might use this instead of officeName
    private double latitude;
    private double longitude;

    // Empty constructor required for Firestore
    public AttendanceRecord() {
    }

    public AttendanceRecord(String id, String date, String status, String time, Timestamp timestamp, 
                          String type, String userId, String userName, double verificationConfidence, 
                          String sevarthId, String locationId, String officeName, double latitude, double longitude) {
        this.id = id;
        this.date = date;
        this.status = status;
        this.time = time;
        this.timestamp = timestamp;
        this.type = type;
        this.userId = userId;
        this.userName = userName;
        this.verificationConfidence = verificationConfidence;
        this.sevarthId = sevarthId;
        this.locationId = locationId;
        this.officeName = officeName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters and setters
    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDate() {
        return date != null ? date : "";
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStatus() {
        return status != null ? status : "Unknown";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTime() {
        return time != null ? time : "";
    }

    public void setTime(String time) {
        this.time = time;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type != null ? type : "unknown";
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId != null ? userId : "";
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName != null ? userName : "Unknown User";
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public double getVerificationConfidence() {
        return verificationConfidence;
    }

    public void setVerificationConfidence(double verificationConfidence) {
        this.verificationConfidence = verificationConfidence;
    }

    public String getSevarthId() {
        return sevarthId != null ? sevarthId : "";
    }

    public void setSevarthId(String sevarthId) {
        this.sevarthId = sevarthId;
    }

    public String getLocationId() {
        return locationId != null ? locationId : "";
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getOfficeName() {
        // If officeName is null/empty, try locationName as alternative
        if (officeName == null || officeName.isEmpty()) {
            return locationName != null ? locationName : "Unknown Office";
        }
        return officeName;
    }

    public void setOfficeName(String officeName) {
        this.officeName = officeName;
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Date getDateObject() {
        return timestamp != null ? timestamp.toDate() : null;
    }

    public boolean isCheckIn() {
        return type != null && "check_in".equals(type);
    }

    public boolean isCheckOut() {
        return type != null && "check_out".equals(type);
    }
} 