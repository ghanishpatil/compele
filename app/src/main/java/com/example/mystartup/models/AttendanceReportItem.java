package com.example.mystartup.models;

import java.util.Objects;

/**
 * Model class to represent a daily attendance report item
 * that groups check-in and check-out times for a single day
 */
public class AttendanceReportItem {
    private String date;
    private String officeName;
    private String checkInTime;
    private String checkOutTime;
    
    public AttendanceReportItem(String date, String officeName) {
        this.date = date;
        this.officeName = officeName;
        this.checkInTime = "-";
        this.checkOutTime = "-";
    }
    
    public String getDate() {
        return date;
    }
    
    public void setDate(String date) {
        this.date = date;
    }
    
    public String getOfficeName() {
        return officeName;
    }
    
    public void setOfficeName(String officeName) {
        this.officeName = officeName;
    }
    
    public String getCheckInTime() {
        return checkInTime;
    }
    
    public void setCheckInTime(String checkInTime) {
        this.checkInTime = checkInTime;
    }
    
    public String getCheckOutTime() {
        return checkOutTime;
    }
    
    public void setCheckOutTime(String checkOutTime) {
        this.checkOutTime = checkOutTime;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttendanceReportItem that = (AttendanceReportItem) o;
        return Objects.equals(date, that.date) && 
               Objects.equals(officeName, that.officeName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(date, officeName);
    }
} 