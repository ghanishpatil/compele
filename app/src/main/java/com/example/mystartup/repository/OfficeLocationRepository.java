package com.example.mystartup.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mystartup.api.LocationRequest;
import com.example.mystartup.api.LocationResponse;
import com.example.mystartup.api.OfficeLocationApiService;
import com.example.mystartup.api.RetrofitClient;
import com.example.mystartup.models.OfficeLocation;
import com.example.mystartup.utils.ApiResponse;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class OfficeLocationRepository {
    private static final String TAG = "OfficeLocationRepo";
    private final OfficeLocationApiService apiService;
    private final FirebaseFirestore db;
    private final MutableLiveData<List<OfficeLocation>> locationsLiveData;
    private final MutableLiveData<String> errorLiveData;

    public OfficeLocationRepository() {
        apiService = RetrofitClient.getInstance().getOfficeLocationApiService();
        db = FirebaseFirestore.getInstance();
        locationsLiveData = new MutableLiveData<>();
        errorLiveData = new MutableLiveData<>();
    }

    public LiveData<List<OfficeLocation>> getLocations() {
        return locationsLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<ApiResponse<List<OfficeLocation>>> fetchLocations(String token) {
        MutableLiveData<ApiResponse<List<OfficeLocation>>> result = new MutableLiveData<>();
        result.setValue(ApiResponse.loading());

        apiService.getAllLocations("Bearer " + token).enqueue(new Callback<List<OfficeLocation>>() {
            @Override
            public void onResponse(Call<List<OfficeLocation>> call, Response<List<OfficeLocation>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    result.setValue(ApiResponse.success(response.body()));
                    locationsLiveData.setValue(response.body());
                } else {
                    result.setValue(ApiResponse.error("Failed to fetch locations"));
                    errorLiveData.setValue("Failed to fetch locations");
                }
            }

            @Override
            public void onFailure(Call<List<OfficeLocation>> call, Throwable t) {
                String errorMessage = "Network error: " + t.getMessage();
                result.setValue(ApiResponse.error(errorMessage));
                errorLiveData.setValue(errorMessage);
                Log.e(TAG, "Error fetching locations", t);
            }
        });

        return result;
    }

    public LiveData<ApiResponse<OfficeLocation>> getLocationById(String token, String locationId) {
        MutableLiveData<ApiResponse<OfficeLocation>> result = new MutableLiveData<>();
        result.setValue(ApiResponse.loading());

        apiService.getLocationById("Bearer " + token, locationId).enqueue(new Callback<OfficeLocation>() {
            @Override
            public void onResponse(Call<OfficeLocation> call, Response<OfficeLocation> response) {
                if (response.isSuccessful() && response.body() != null) {
                    result.setValue(ApiResponse.success(response.body()));
                } else {
                    result.setValue(ApiResponse.error("Failed to fetch location"));
                }
            }

            @Override
            public void onFailure(Call<OfficeLocation> call, Throwable t) {
                String errorMessage = "Network error: " + t.getMessage();
                result.setValue(ApiResponse.error(errorMessage));
                Log.e(TAG, "Error fetching location by ID", t);
            }
        });

        return result;
    }

    public void addLocation(String token, OfficeLocation location, final OnLocationAddedCallback callback) {
        // Convert OfficeLocation to LocationRequest
        LocationRequest request = new LocationRequest(
            location.getName(),
            location.getAddress(),
            location.getCity(),
            location.getState(),
            location.getZipCode(),
            location.getLatitude(),
            location.getLongitude()
        );

        apiService.addLocation("Bearer " + token, request).enqueue(new Callback<LocationResponse>() {
            @Override
            public void onResponse(Call<LocationResponse> call, Response<LocationResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getLocation());
                } else {
                    String errorMessage = response.body() != null ? response.body().getMessage() : "Failed to add location";
                    callback.onError(errorMessage);
                }
            }

            @Override
            public void onFailure(Call<LocationResponse> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
                Log.e(TAG, "Error adding location", t);
            }
        });
    }

    public interface OnLocationAddedCallback {
        void onSuccess(OfficeLocation location);
        void onError(String error);
    }

    public LiveData<ApiResponse<OfficeLocation>> addLocation(String token, OfficeLocation location) {
        MutableLiveData<ApiResponse<OfficeLocation>> resultLiveData = new MutableLiveData<>();
        resultLiveData.setValue(ApiResponse.loading());

        // Convert OfficeLocation to LocationRequest
        LocationRequest request = new LocationRequest(
            location.getName(),
            location.getAddress(),
            location.getCity(),
            location.getState(),
            location.getZipCode(),
            location.getLatitude(),
            location.getLongitude()
        );

        apiService.addLocation("Bearer " + token, request).enqueue(new Callback<LocationResponse>() {
            @Override
            public void onResponse(Call<LocationResponse> call, Response<LocationResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    resultLiveData.setValue(ApiResponse.success(response.body().getLocation()));
                } else {
                    String errorMessage = response.body() != null ? response.body().getMessage() : "Failed to add location";
                    resultLiveData.setValue(ApiResponse.error(errorMessage));
                }
            }

            @Override
            public void onFailure(Call<LocationResponse> call, Throwable t) {
                resultLiveData.setValue(ApiResponse.error("Network error: " + t.getMessage()));
                Log.e(TAG, "Error adding location", t);
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResponse<OfficeLocation>> updateLocation(String token, String locationId, OfficeLocation location) {
        MutableLiveData<ApiResponse<OfficeLocation>> resultLiveData = new MutableLiveData<>();
        resultLiveData.setValue(ApiResponse.loading());

        apiService.updateLocation("Bearer " + token, locationId, location).enqueue(new Callback<OfficeLocation>() {
            @Override
            public void onResponse(Call<OfficeLocation> call, Response<OfficeLocation> response) {
                if (response.isSuccessful() && response.body() != null) {
                    resultLiveData.setValue(ApiResponse.success(response.body()));
                } else {
                    resultLiveData.setValue(ApiResponse.error("Failed to update office location"));
                }
            }

            @Override
            public void onFailure(Call<OfficeLocation> call, Throwable t) {
                resultLiveData.setValue(ApiResponse.error(t.getMessage()));
            }
        });

        return resultLiveData;
    }

    public LiveData<ApiResponse<Void>> deleteLocation(String token, String locationId) {
        MutableLiveData<ApiResponse<Void>> resultLiveData = new MutableLiveData<>();
        resultLiveData.setValue(ApiResponse.loading());

        apiService.deleteLocation("Bearer " + token, locationId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    resultLiveData.setValue(ApiResponse.success(null));
                } else {
                    resultLiveData.setValue(ApiResponse.error("Failed to delete office location"));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                resultLiveData.setValue(ApiResponse.error(t.getMessage()));
            }
        });

        return resultLiveData;
    }
} 