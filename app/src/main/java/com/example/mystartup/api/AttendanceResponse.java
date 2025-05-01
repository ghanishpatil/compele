package com.example.mystartup.api;

import com.google.gson.annotations.SerializedName;

/**
 * Response model for attendance marking
 */
public class AttendanceResponse {
    @SerializedName("message")
    private String message;
    
    @SerializedName("attendance_id")
    private String attendanceId;
    
    @SerializedName("date")
    private String date;
    
    @SerializedName("time")
    private String time;
    
    @SerializedName("success")
    private boolean success = true; // Default to true for backward compatibility
    
    // Helper method to check if response indicates an error
    public boolean isError() {
        return !success || 
               (message != null && message.toLowerCase().contains("error")) ||
               (message != null && message.toLowerCase().contains("incomplete"));
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getAttendanceId() {
        return attendanceId;
    }
    
    public void setAttendanceId(String attendanceId) {
        this.attendanceId = attendanceId;
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
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    @Override
    public String toString() {
        return "AttendanceResponse{" +
                "message='" + message + '\'' +
                ", attendanceId='" + attendanceId + '\'' +
                ", date='" + date + '\'' +
                ", time='" + time + '\'' +
                ", success=" + success +
                '}';
    }
} 