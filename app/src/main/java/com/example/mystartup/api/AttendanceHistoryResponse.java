package com.example.mystartup.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response model for attendance history
 */
public class AttendanceHistoryResponse {
    @SerializedName("attendance_records")
    private List<AttendanceRecord> attendanceRecords;
    
    @SerializedName("count")
    private int count;
    
    public List<AttendanceRecord> getAttendanceRecords() {
        return attendanceRecords;
    }
    
    public void setAttendanceRecords(List<AttendanceRecord> attendanceRecords) {
        this.attendanceRecords = attendanceRecords;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
    
    /**
     * Inner class representing a single attendance record
     */
    public static class AttendanceRecord {
        @SerializedName("id")
        private String id;
        
        @SerializedName("userId")
        private String userId;
        
        @SerializedName("userName")
        private String userName;
        
        @SerializedName("date")
        private String date;
        
        @SerializedName("time")
        private String time;
        
        @SerializedName("type")
        private String type;
        
        @SerializedName("status")
        private String status;
        
        @SerializedName("verificationConfidence")
        private float verificationConfidence;
        
        @SerializedName("officeName")
        private String officeName;
        
        // Getters and setters
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
        
        public String getUserName() {
            return userName;
        }
        
        public void setUserName(String userName) {
            this.userName = userName;
        }
        
        public String getDate() {
            return date;
        }
        
        public void setDate(String date) {
            this.date = date;
        }
        
        public String getTime() {
            return time;
        }
        
        public void setTime(String time) {
            this.time = time;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public float getVerificationConfidence() {
            return verificationConfidence;
        }
        
        public void setVerificationConfidence(float verificationConfidence) {
            this.verificationConfidence = verificationConfidence;
        }
        
        public String getOfficeName() {
            return officeName;
        }
        
        public void setOfficeName(String officeName) {
            this.officeName = officeName;
        }
    }
} 