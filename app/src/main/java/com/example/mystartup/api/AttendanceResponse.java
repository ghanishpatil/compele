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
} 