package com.example.mystartup.models;

import com.google.firebase.firestore.Exclude;
import com.google.gson.annotations.SerializedName;

import java.util.Date;

public class OfficeLocation {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("address")
    private String address;

    @SerializedName("city")
    private String city;

    @SerializedName("state")
    private String state;

    @SerializedName("zip_code")
    private String zipCode;
    
    @SerializedName("taluka")
    private String taluka;

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("phoneNumber")
    private String phoneNumber;

    @SerializedName("emailAddress")
    private String emailAddress;

    @SerializedName("isActive")
    private boolean isActive;

    @SerializedName("radius")
    private Integer radius;

    private String createdBy;
    private Date createdAt;
    private Date updatedAt;
    
    // Required empty constructor for Firestore
    public OfficeLocation() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }
    
    public OfficeLocation(String id, String name, String address, String city, String state, 
                          String zipCode, Double latitude, Double longitude, String phoneNumber, String emailAddress, boolean isActive) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phoneNumber = phoneNumber;
        this.emailAddress = emailAddress;
        this.isActive = isActive;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }
    
    // Constructor used in AddOfficeLocationActivity
    public OfficeLocation(String id, String name, String taluka, Double latitude, Double longitude, Integer radius, String createdBy) {
        this.id = id;
        this.name = name;
        this.taluka = taluka;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
        this.createdBy = createdBy;
        this.isActive = true;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    public String getZipCode() {
        return zipCode;
    }
    
    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }
    
    public String getTaluka() {
        return taluka;
    }
    
    public void setTaluka(String taluka) {
        this.taluka = taluka;
    }
    
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    public Integer getRadius() {
        return radius;
    }
    
    public void setRadius(Integer radius) {
        this.radius = radius;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getEmailAddress() {
        return emailAddress;
    }
    
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
    public Date getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
        
        // Always update updatedAt when changes are made
        this.updatedAt = new Date();
    }
    
    @Exclude
    public String getFormattedCoordinates() {
        return latitude + ", " + longitude;
    }
} 