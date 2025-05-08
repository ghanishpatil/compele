package com.example.mystartup;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.mystartup.utils.PermissionUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceCaptureActivity extends AppCompatActivity {
    private static final String TAG = "FaceCaptureActivity";
    private static final int REQUEST_CODE_PERMISSIONS = PermissionUtils.CAMERA_PERMISSION_REQUEST_CODE;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView viewFinder;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private File outputDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_capture);

        viewFinder = findViewById(R.id.viewFinder);
        FloatingActionButton captureButton = findViewById(R.id.camera_capture_button);

        // Set up the capture button listener
        captureButton.setOnClickListener(v -> takePhoto());

        outputDirectory = getOutputDirectory();
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        // Request camera permission with better UX
        checkCameraPermissionAndStartCamera();
    }
    
    private void checkCameraPermissionAndStartCamera() {
        if (PermissionUtils.hasCameraPermission(this)) {
            startCamera();
        } else {
            // Check if we should show rationale
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                // Show explanation dialog
                PermissionUtils.showPermissionExplanationDialog(
                    this,
                    "Camera Permission Required",
                    "This app needs camera access to capture your face for attendance marking. " +
                    "Without this permission, you won't be able to use the face recognition feature.",
                    REQUEST_CODE_PERMISSIONS,
                    null
                );
            } else {
                // Directly request permission
                PermissionUtils.requestCameraPermission(this);
            }
        }
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        // Create output file
        File photoFile = new File(outputDirectory, new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri savedUri = Uri.fromFile(photoFile);
                        String msg = "Photo capture succeeded: " + savedUri.toString();
                        Log.d(TAG, msg);
                        
                        // Return the image path to the calling activity
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("image_path", photoFile.getAbsolutePath());
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                        Toast.makeText(FaceCaptureActivity.this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up the preview use case
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // Set up the capture use case
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // Select front camera
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage());
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private File getOutputDirectory() {
        File mediaDir = new File(getExternalFilesDir(null), "reference_images");
        if (!mediaDir.exists()) {
            mediaDir.mkdirs();
        }
        return mediaDir;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted");
                startCamera();
            } else {
                Log.d(TAG, "Camera permission denied");
                
                // Check if user checked "Don't ask again"
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                    // User checked "Don't ask again", show settings dialog
                    PermissionUtils.showSettingsDialog(
                        this,
                        "Camera permission is required for face recognition. " +
                        "Please enable it in app settings."
                    );
                } else {
                    Toast.makeText(this, "Camera permission is required for face recognition", Toast.LENGTH_LONG).show();
                }
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
} 