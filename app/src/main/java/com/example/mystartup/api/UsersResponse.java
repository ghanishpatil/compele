package com.example.mystartup.api;

import com.example.mystartup.models.User;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class UsersResponse {
    @SerializedName("users")
    private List<User> users;
    
    @SerializedName("success")
    private boolean success;
    
    @SerializedName("message")
    private String message;
    
    public List<User> getUsers() {
        return users;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
} 