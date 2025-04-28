package com.example.mystartup.api;

import com.example.mystartup.models.OfficeLocation;
import com.google.gson.annotations.SerializedName;

public class LocationResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private OfficeLocation location;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public OfficeLocation getLocation() {
        return location;
    }
} 