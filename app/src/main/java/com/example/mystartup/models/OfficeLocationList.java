package com.example.mystartup.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OfficeLocationList {
    @SerializedName("office_locations")
    private List<OfficeLocation> officeLocations;

    public OfficeLocationList() {
    }

    public OfficeLocationList(List<OfficeLocation> officeLocations) {
        this.officeLocations = officeLocations;
    }

    public List<OfficeLocation> getOfficeLocations() {
        return officeLocations;
    }

    public void setOfficeLocations(List<OfficeLocation> officeLocations) {
        this.officeLocations = officeLocations;
    }
} 