package com.example.mystartup.api;

import com.example.mystartup.models.OfficeLocation;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class LocationsResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private List<OfficeLocation> locations;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<OfficeLocation> getLocations() {
        return locations;
    }
} 