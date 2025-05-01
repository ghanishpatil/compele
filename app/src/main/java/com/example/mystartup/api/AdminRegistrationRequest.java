package com.example.mystartup.api;

import com.google.gson.annotations.SerializedName;

/**
 * Request model for registering a user through admin API
 */
public class AdminRegistrationRequest {
    @SerializedName("sevarth_id")
    private String sevarthId;
    
    @SerializedName("first_name")
    private String firstName;
    
    @SerializedName("last_name")
    private String lastName;
    
    @SerializedName("gender")
    private String gender;
    
    @SerializedName("date_of_birth")
    private String dateOfBirth;
    
    @SerializedName("phone_number")
    private String phoneNumber;
    
    @SerializedName("email")
    private String email;
    
    @SerializedName("location_id")
    private String locationId;
    
    @SerializedName("location_name")
    private String locationName;
    
    @SerializedName("password")
    private String password;
    
    public AdminRegistrationRequest() {
    }
    
    public AdminRegistrationRequest(String sevarthId, String firstName, String lastName,
                                   String gender, String dateOfBirth, String phoneNumber,
                                   String email, String locationId, String locationName,
                                   String password) {
        this.sevarthId = sevarthId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.locationId = locationId;
        this.locationName = locationName;
        this.password = password;
    }
    
    // Getters and setters
    public String getSevarthId() {
        return sevarthId;
    }
    
    public void setSevarthId(String sevarthId) {
        this.sevarthId = sevarthId;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getGender() {
        return gender;
    }
    
    public void setGender(String gender) {
        this.gender = gender;
    }
    
    public String getDateOfBirth() {
        return dateOfBirth;
    }
    
    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getLocationId() {
        return locationId;
    }
    
    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
} 