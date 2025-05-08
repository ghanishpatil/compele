package com.example.mystartup.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Utility class for handling runtime permissions
 */
public class PermissionUtils {
    
    public static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 101;
    
    /**
     * Check if camera permission is granted
     * @param context The context
     * @return true if permission is granted, false otherwise
     */
    public static boolean hasCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, 
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Request camera permission
     * @param activity The activity
     */
    public static void requestCameraPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }
    
    /**
     * Check if location permissions are granted
     * @param context The context
     * @return true if permissions are granted, false otherwise
     */
    public static boolean hasLocationPermissions(Context context) {
        return ContextCompat.checkSelfPermission(context, 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, 
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Request location permissions
     * @param activity The activity
     */
    public static void requestLocationPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE);
    }
    
    /**
     * Show dialog explaining why permission is needed and prompt user to grant it
     * @param activity The activity
     * @param title Dialog title
     * @param message Dialog message
     * @param permissionRequestCode The permission request code
     * @param permissionCallback Optional callback after user action
     */
    public static void showPermissionExplanationDialog(
            Activity activity,
            String title,
            String message,
            int permissionRequestCode,
            Runnable permissionCallback) {
        
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    if (permissionRequestCode == CAMERA_PERMISSION_REQUEST_CODE) {
                        requestCameraPermission(activity);
                    } else if (permissionRequestCode == LOCATION_PERMISSION_REQUEST_CODE) {
                        requestLocationPermissions(activity);
                    }
                    if (permissionCallback != null) {
                        permissionCallback.run();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }
    
    /**
     * Show settings dialog if permission is permanently denied
     * @param activity The activity
     * @param message Dialog message
     */
    public static void showSettingsDialog(Activity activity, String message) {
        new AlertDialog.Builder(activity)
                .setTitle("Permission Required")
                .setMessage(message)
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                    intent.setData(uri);
                    activity.startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }
} 