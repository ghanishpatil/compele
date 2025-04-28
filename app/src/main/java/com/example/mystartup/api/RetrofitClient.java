package com.example.mystartup.api;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton class to manage Retrofit instance
 */
public class RetrofitClient {
    // Using your computer's actual IP address
    private static final String BASE_URL = "http://192.168.1.7:5000/";
    
    private static RetrofitClient instance = null;
    private final Retrofit retrofit;
    private final ApiService apiService;
    private final OfficeLocationApiService officeLocationApiService;
    private final FaceRecognitionApiService faceRecognitionApiService;

    private RetrofitClient() {
        // Create logging interceptor
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Configure OkHttpClient with timeouts and logging
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .build();

        // Create Retrofit instance
        retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

        // Create API services
        apiService = retrofit.create(ApiService.class);
        officeLocationApiService = retrofit.create(OfficeLocationApiService.class);
        faceRecognitionApiService = retrofit.create(FaceRecognitionApiService.class);
    }

    public static synchronized RetrofitClient getInstance() {
        if (instance == null) {
            instance = new RetrofitClient();
        }
        return instance;
    }

    public ApiService getApiService() {
        return apiService;
    }
    
    public OfficeLocationApiService getOfficeLocationApiService() {
        return officeLocationApiService;
    }

    public FaceRecognitionApiService getFaceRecognitionApiService() {
        return faceRecognitionApiService;
    }

    public String getBaseUrl() {
        return BASE_URL;
    }
} 