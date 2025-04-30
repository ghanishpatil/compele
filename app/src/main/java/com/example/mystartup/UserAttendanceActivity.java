package com.example.mystartup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mystartup.fragments.UserAttendanceFragment;

public class UserAttendanceActivity extends AppCompatActivity {
    private static final String TAG = "UserAttendanceActivity";
    private static final String PREF_NAME = "AuthPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_attendance);
        
        // Get location details from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String locationId = prefs.getString("selected_location_id", "");
        String locationName = prefs.getString("selected_location_name", "");
        float latitude = prefs.getFloat("selected_location_latitude", 0f);
        float longitude = prefs.getFloat("selected_location_longitude", 0f);
        int radius = prefs.getInt("selected_location_radius", 100);
        
        // Log the location details being passed to the fragment
        Log.d(TAG, "Loading location from SharedPreferences:");
        Log.d(TAG, "  Location ID: " + locationId);
        Log.d(TAG, "  Location Name: " + locationName);
        Log.d(TAG, "  Latitude: " + latitude);
        Log.d(TAG, "  Longitude: " + longitude);
        Log.d(TAG, "  Radius: " + radius + "m");
        
        // Check if coordinates are valid
        if (latitude == 0f && longitude == 0f) {
            Log.w(TAG, "WARNING: Office coordinates are (0,0) which is likely invalid!");
        }
        
        // Create fragment with location data
        UserAttendanceFragment fragment = new UserAttendanceFragment();
        
        // Pass location data to fragment
        Bundle args = new Bundle();
        args.putString("location_id", locationId);
        args.putString("location_name", locationName);
        args.putFloat("location_latitude", latitude);
        args.putFloat("location_longitude", longitude);
        args.putInt("location_radius", radius);
        fragment.setArguments(args);
        
        // Add the UserAttendanceFragment if this is the first creation
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        }
    }

    @Override
    public void onBackPressed() {
        // Navigate back to LocationSelectionActivity instead of closing the app
        Intent intent = new Intent(this, LocationSelectionActivity.class);
        startActivity(intent);
        finish();
    }
} 