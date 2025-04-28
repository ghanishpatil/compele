package com.example.mystartup.api;

import com.example.mystartup.models.OfficeLocation;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface OfficeLocationApiService {
    
    @GET("admin/locations")
    Call<LocationsResponse> getLocations(@Header("Authorization") String token);
    
    @GET("office-locations")
    Call<List<OfficeLocation>> getAllLocations(@Header("Authorization") String token);
    
    @GET("office-locations/{id}")
    Call<OfficeLocation> getLocationById(@Header("Authorization") String token, @Path("id") String locationId);
    
    @POST("admin/locations")
    Call<LocationResponse> addLocation(@Header("Authorization") String token, @Body LocationRequest request);
    
    @PUT("office-locations/{id}")
    Call<OfficeLocation> updateLocation(@Header("Authorization") String token, @Path("id") String locationId, @Body OfficeLocation location);
    
    @DELETE("office-locations/{id}")
    Call<Void> deleteLocation(@Header("Authorization") String token, @Path("id") String locationId);
} 