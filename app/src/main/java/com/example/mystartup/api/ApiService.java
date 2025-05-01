package com.example.mystartup.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {
    @POST("login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("auth/register/admin")
    Call<AdminRegistrationResponse> registerAdmin(@Body AdminRegistrationRequest request);
    
    @POST("register/user")
    Call<AdminRegistrationResponse> registerUser(@Header("Authorization") String token, @Body AdminRegistrationRequest request);

    @GET("admin/users")
    Call<UsersResponse> getUsers(@Header("Authorization") String token);
    
    @DELETE("admin/users/{sevarth_id}")
    Call<Void> deleteUser(@Header("Authorization") String token, @Path("sevarth_id") String sevarthId);
} 