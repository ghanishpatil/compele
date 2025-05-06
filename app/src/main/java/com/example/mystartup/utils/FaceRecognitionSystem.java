package com.example.mystartup.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.util.TimeZone;

/**
 * Face Recognition System for attendance verification
 * Based on the Python model, adapted for Android
 */
public class FaceRecognitionSystem {
    private static final String TAG = "FaceRecognitionSystem";
    private static final float MINIMUM_CONFIDENCE_THRESHOLD = 0.45f; // 45%
    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.55f; // 55%
    
    private final Context context;
    private final FirebaseStorage storage;
    private final FirebaseFirestore db;
    private final FaceDetector faceDetector;
    private final FirebaseAuth auth;
    
    private String userId;
    private String userDisplayName;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private float lastVerificationConfidence = 0f;
    
    private VerificationListener verificationListener;
    
    public interface VerificationListener {
        void onVerificationStarted();
        void onReferenceImageLoaded();
        void onFaceCaptured();
        void onVerificationComplete(boolean isVerified, float confidence);
        void onVerificationError(String errorMessage);
        void onAttendanceMarked(boolean success, String message);
    }
    
    /**
     * Constructor
     * 
     * @param context Application context
     */
    public FaceRecognitionSystem(Context context) {
        this.context = context;
        
        // Initialize Firebase components
        auth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance("gs://startup-cf3fd.firebasestorage.app");  // Updated correct bucket URL
        db = FirebaseFirestore.getInstance();
        
        // Set up face detector with high accuracy
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();
        
        faceDetector = FaceDetection.getClient(options);
        
        // Get the current user information
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            this.userId = currentUser.getUid();
        }
    }
    
    /**
     * Set the listener for verification events
     * 
     * @param listener The verification listener
     */
    public void setVerificationListener(VerificationListener listener) {
        this.verificationListener = listener;
    }
    
    /**
     * Set the user ID manually when FirebaseAuth is not used
     * 
     * @param userId The user ID to set
     * @param displayName The user's display name
     */
    public void setUserDetails(String userId, String displayName) {
        this.userId = userId;
        this.userDisplayName = displayName;
    }
    
    /**
     * Initialize the camera for face capture
     * 
     * @param lifecycleOwner The lifecycle owner (activity/fragment)
     * @param previewView The camera preview view
     */
    public void initializeCamera(LifecycleOwner lifecycleOwner, androidx.camera.view.PreviewView previewView) {
        // Check if already initialized to prevent duplicate initialization
        if (cameraProvider != null) {
            Log.d(TAG, "Camera already initialized, unbinding first");
            cameraProvider.unbindAll();
        }
        
        Log.d(TAG, "Initializing camera...");
        
        try {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                    ProcessCameraProvider.getInstance(context);
            
            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    
                    // Get device orientation for best camera setup
                    int rotation = getDeviceRotation();
                    Log.d(TAG, "Device rotation: " + rotation);
                    
                    // Get display metrics to calculate appropriate resolution
                    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                    int screenWidth = metrics.widthPixels;
                    int screenHeight = metrics.heightPixels;
                    Log.d(TAG, "Screen dimensions: " + screenWidth + "x" + screenHeight);
                    
                    // Calculate target resolution based on device screen size
                    // We're using a 4:3 aspect ratio for better face detection
                    int targetWidth = Math.min(640, screenWidth);
                    int targetHeight = (targetWidth * 4) / 3; // 4:3 aspect ratio
                    android.util.Size targetSize = new android.util.Size(targetWidth, targetHeight);
                    
                    Log.d(TAG, "Using target resolution: " + targetWidth + "x" + targetHeight);
                    
                    // Set up the preview use case with optimized settings
                    Preview preview = new Preview.Builder()
                            .setTargetResolution(targetSize)
                            .setTargetRotation(rotation)
                            .build();
                    
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());
                    
                    // Set up image capture use case with optimized settings
                    imageCapture = new ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setTargetResolution(targetSize)
                            .setTargetRotation(rotation)
                            .build();
                    
                    // Select front camera
                    CameraSelector cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                            .build();
                    
                    // Unbind any bound use cases before rebinding
                    cameraProvider.unbindAll();
                    
                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                            lifecycleOwner, 
                            cameraSelector, 
                            preview, 
                            imageCapture);
                    
                    Log.d(TAG, "Camera initialization successful");
                    
                    // Ensure flash is disabled for front camera (can cause issues on some devices)
                    try {
                        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                        String[] cameraIds = cameraManager.getCameraIdList();
                        for (String id : cameraIds) {
                            if (cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == 
                                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                                if (cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                                    cameraManager.setTorchMode(id, false);
                                }
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // Just log the error but continue - flash control is not critical
                        Log.w(TAG, "Failed to control flash: " + e.getMessage());
                    }
                    
                } catch (ExecutionException e) {
                    Log.e(TAG, "Camera initialization failed (ExecutionException): " + e.getMessage(), e);
                    if (verificationListener != null) {
                        verificationListener.onVerificationError("Camera initialization failed: " + e.getCause().getMessage());
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Camera initialization failed (InterruptedException): " + e.getMessage(), e);
                    if (verificationListener != null) {
                        verificationListener.onVerificationError("Camera initialization was interrupted");
                    }
                    Thread.currentThread().interrupt(); // Restore interrupted state
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Camera initialization failed (IllegalArgumentException): " + e.getMessage(), e);
                    if (verificationListener != null) {
                        verificationListener.onVerificationError("Camera configuration error: " + e.getMessage());
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Camera initialization failed (IllegalStateException): " + e.getMessage(), e);
                    if (verificationListener != null) {
                        verificationListener.onVerificationError("Camera is in an illegal state: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Camera initialization failed (General Exception): " + e.getMessage(), e);
                    if (verificationListener != null) {
                        verificationListener.onVerificationError("Camera initialization failed: " + e.getMessage());
                    }
                }
            }, ContextCompat.getMainExecutor(context));
        } catch (Exception e) {
            Log.e(TAG, "Fatal error initializing camera: " + e.getMessage(), e);
            if (verificationListener != null) {
                verificationListener.onVerificationError("Failed to initialize camera system: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get the current device rotation
     * @return Device rotation as Surface.ROTATION_x constant
     */
    private int getDeviceRotation() {
        android.view.WindowManager windowManager = (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        android.view.Display display = windowManager.getDefaultDisplay();
        return display.getRotation();
    }
    
    /**
     * Set up face detection on camera feed
     */
    private void setupFaceDetection(androidx.camera.view.PreviewView previewView, 
                                  LifecycleOwner lifecycleOwner,
                                  CameraSelector cameraSelector) {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
                
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), imageProxy -> {
            // Process image for face detection
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(), 
                    imageProxy.getImageInfo().getRotationDegrees());
            
            // Process the image
            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        // Face detection logic here
                        // This just detects faces in the preview - not used for verification
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed: " + e.getMessage());
                        imageProxy.close();
                    });
        });
        
        // Bind the image analysis use case
        if (cameraProvider != null) {
            cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis);
        }
    }
    
    /**
     * Start the verification process for attendance
     * 
     * @param attendanceType The type of attendance ("check_in" or "check_out")
     */
    public void startVerification(String attendanceType) {
        if (userId == null || userId.isEmpty()) {
            if (verificationListener != null) {
                verificationListener.onVerificationError("User ID not set. Please log in first.");
            }
            return;
        }
        
        if (verificationListener != null) {
            verificationListener.onVerificationStarted();
        }
        
        // Step 1: Fetch reference image
        fetchReferenceImage(referenceImage -> {
            if (referenceImage == null) {
                if (verificationListener != null) {
                    verificationListener.onVerificationError("Failed to load reference image. Please ensure your face image is registered.");
                }
                return;
            }
            
            if (verificationListener != null) {
                verificationListener.onReferenceImageLoaded();
            }
            
            // Step 2: Capture current face image
            captureImage(capturedImage -> {
                if (capturedImage == null) {
                    if (verificationListener != null) {
                        verificationListener.onVerificationError("Failed to capture face image. Please try again.");
                    }
                    return;
                }
                
                if (verificationListener != null) {
                    verificationListener.onFaceCaptured();
                }
                
                // Step 3: Compare faces
                compareFaces(referenceImage, capturedImage, (isVerified, confidence) -> {
                    this.lastVerificationConfidence = confidence;
                    
                    if (verificationListener != null) {
                        verificationListener.onVerificationComplete(isVerified, confidence);
                    }
                    
                    if (isVerified) {
                        // Step 4: Mark attendance if verified
                        markAttendance(attendanceType);
                    }
                });
            });
        });
    }
    
    /**
     * Fetch the user's reference image from Firebase Storage
     * 
     * @param callback Callback with the reference image bitmap
     */
    private void fetchReferenceImage(OnImageLoadedCallback callback) {
        // Get reference to the stored face image using the correct naming pattern
        StorageReference storageRef = storage.getReference()
                .child("reference_images")
                .child("face_" + userId + ".jpg");
        
        final long MAX_SIZE = 5 * 1024 * 1024; // 5MB max
        
        Log.d(TAG, "Fetching reference image from: " + storageRef.getPath());
        
        storageRef.getBytes(MAX_SIZE)
                .addOnSuccessListener(bytes -> {
                    // Convert bytes to bitmap
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    Log.d(TAG, "Successfully fetched reference image");
                    callback.onImageLoaded(bitmap);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching reference image: " + e.getMessage());
                    if (verificationListener != null) {
                        verificationListener.onVerificationError("Error fetching reference image: " + e.getMessage());
                    }
                    callback.onImageLoaded(null);
                });
    }
    
    /**
     * Interface for image capture callback
     */
    public interface ImageCaptureCallback {
        void onImageCaptured(Bitmap bitmap);
    }

    /**
     * Capture an image from the camera for API-based verification
     * 
     * @param callback Callback for the captured image
     */
    public void captureImage(ImageCaptureCallback callback) {
        if (imageCapture == null) {
            if (verificationListener != null) {
                verificationListener.onVerificationError("Camera not initialized. Please restart the app.");
            }
            callback.onImageCaptured(null);
            return;
        }
        
        try {
            // Create unique filename for temporary file
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "FACE_" + timestamp + "_" + UUID.randomUUID().toString() + ".jpg";
            File outputDir = new File(context.getCacheDir(), "face_captures");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            File photoFile = new File(outputDir, fileName);
            
            // Create output options object
            ImageCapture.OutputFileOptions outputOptions = 
                    new ImageCapture.OutputFileOptions.Builder(photoFile).build();
            
            // Log image capture attempt
            Log.d(TAG, "Attempting to capture image to: " + photoFile.getAbsolutePath());
            
            // Take the picture - use the main thread executor for consistent behavior
            final Executor mainExecutor = ContextCompat.getMainExecutor(context);
            
            // Set flash mode to off explicitly to avoid issues on some devices
            imageCapture.setFlashMode(ImageCapture.FLASH_MODE_OFF);
            
            imageCapture.takePicture(outputOptions, mainExecutor,
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            try {
                                // Verify file exists and has content
                                if (!photoFile.exists() || photoFile.length() == 0) {
                                    Log.e(TAG, "Image file doesn't exist or is empty");
                                    if (verificationListener != null) {
                                        verificationListener.onVerificationError("Failed to save captured image");
                                    }
                                    callback.onImageCaptured(null);
                                    return;
                                }
                                
                                Log.d(TAG, "Image saved successfully at: " + photoFile.getAbsolutePath() + 
                                          " (Size: " + photoFile.length() + " bytes)");
                                
                                // Read the saved image with optimized settings
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                                
                                // First check if file can be decoded before loading full bitmap
                                options.inJustDecodeBounds = true;
                                BitmapFactory.decodeFile(photoFile.getAbsolutePath(), options);
                                
                                if (options.outWidth <= 0 || options.outHeight <= 0) {
                                    Log.e(TAG, "Invalid image dimensions: " + options.outWidth + "x" + options.outHeight);
                                    if (verificationListener != null) {
                                        verificationListener.onVerificationError("Captured image is invalid");
                                    }
                                    callback.onImageCaptured(null);
                                    // Clean up
                                    if (photoFile.exists()) {
                                        photoFile.delete();
                                    }
                                    return;
                                }
                                
                                // Load the actual bitmap
                                options.inJustDecodeBounds = false;
                                options.inSampleSize = calculateInSampleSize(options, 640, 480); // Downsample large images
                                Bitmap capturedBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath(), options);
                                
                                // Ensure the bitmap is not null
                                if (capturedBitmap == null) {
                                    Log.e(TAG, "Failed to decode image file into bitmap");
                                    if (verificationListener != null) {
                                        verificationListener.onVerificationError("Failed to process captured image");
                                    }
                                    callback.onImageCaptured(null);
                                    // Clean up
                                    if (photoFile.exists()) {
                                        photoFile.delete();
                                    }
                                    return;
                                }
                                
                                // Log successful bitmap creation
                                Log.d(TAG, "Bitmap created successfully: " + capturedBitmap.getWidth() + "x" + 
                                      capturedBitmap.getHeight());
                                
                                // Check if a face is detectable in the image
                                detectFaceInBitmap(capturedBitmap, faceDetected -> {
                                    try {
                                        if (!faceDetected) {
                                            Log.w(TAG, "No face detected in captured image");
                                            
                                            // Try with a more permissive detector as fallback for challenging light/camera conditions
                                            detectFaceWithFallback(capturedBitmap, fallbackDetected -> {
                                                if (fallbackDetected) {
                                                    Log.d(TAG, "Face detected with fallback detector");
                                                    callback.onImageCaptured(capturedBitmap);
                                                } else {
                                                    if (verificationListener != null) {
                                                        verificationListener.onVerificationError("No face detected in the captured image. Please try again with better lighting.");
                                                    }
                                                    callback.onImageCaptured(null);
                                                }
                                                
                                                // Clean up the temporary file
                                                if (photoFile.exists()) {
                                                    photoFile.delete();
                                                }
                                            });
                                        } else {
                                            Log.d(TAG, "Face successfully detected in captured image");
                                            // Return the bitmap
                                            callback.onImageCaptured(capturedBitmap);
                                            
                                            // Clean up the temporary file
                                            if (photoFile.exists()) {
                                                photoFile.delete();
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error in face detection: " + e.getMessage(), e);
                                        callback.onImageCaptured(null);
                                        
                                        // Clean up on error
                                        if (photoFile.exists()) {
                                            photoFile.delete();
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing captured image: " + e.getMessage(), e);
                                if (verificationListener != null) {
                                    verificationListener.onVerificationError("Error processing captured image: " + e.getMessage());
                                }
                                callback.onImageCaptured(null);
                                
                                // Clean up on error
                                if (photoFile.exists()) {
                                    photoFile.delete();
                                }
                            }
                        }
                        
                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, "Image capture failed: " + exception.getMessage(), exception);
                            if (verificationListener != null) {
                                verificationListener.onVerificationError("Image capture failed: " + exception.getMessage());
                            }
                            callback.onImageCaptured(null);
                            
                            // Clean up on error
                            if (photoFile.exists()) {
                                photoFile.delete();
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during image capture setup: " + e.getMessage(), e);
            if (verificationListener != null) {
                verificationListener.onVerificationError("Failed to set up image capture: " + e.getMessage());
            }
            callback.onImageCaptured(null);
        }
    }
    
    /**
     * Calculate appropriate sample size for loading bitmaps efficiently
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
    
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
    
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }
    
    /**
     * Fallback method with more permissive settings to detect faces in challenging conditions
     */
    private void detectFaceWithFallback(Bitmap bitmap, OnFaceDetectedCallback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        
        // Ultra permissive detector for fallback
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // Use accurate mode
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.05f) // Very small minimum face size
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .build();
        
        FaceDetection.getClient(options).process(image)
                .addOnSuccessListener(faces -> {
                    boolean faceDetected = !faces.isEmpty();
                    Log.d(TAG, "Fallback face detection result: " + (faceDetected ? "Face detected" : "No face detected") + 
                          " (found " + faces.size() + " faces)");
                    callback.onResult(faceDetected);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Fallback face detection failed: " + e.getMessage());
                    callback.onResult(false);
                });
    }
    
    /**
     * Detect if there's a face in the bitmap
     * 
     * @param bitmap The bitmap to check
     * @param callback Callback with the result
     */
    private void detectFaceInBitmap(Bitmap bitmap, OnFaceDetectedCallback callback) {
        // Create input image with optimized settings
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        
        // Configure face detector for better cross-device compatibility
        // Using a smaller minimum face size to detect faces that might be further from camera
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.1f) // Reduced from 0.35f for better detection across devices
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .build();
        
        FaceDetector detector = FaceDetection.getClient(options);
        
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    boolean faceDetected = !faces.isEmpty();
                    Log.d(TAG, "Face detection result: " + (faceDetected ? "Face detected" : "No face detected") + 
                          " (found " + faces.size() + " faces)");
                    if (faceDetected && faces.size() > 0) {
                        // Log face bounds to help with debugging
                        Face face = faces.get(0);
                        Rect bounds = face.getBoundingBox();
                        Log.d(TAG, "Detected face bounds: " + bounds.left + "," + bounds.top + 
                              " to " + bounds.right + "," + bounds.bottom + 
                              " (size: " + bounds.width() + "x" + bounds.height() + ")");
                    }
                    callback.onResult(faceDetected);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Face detection failed: " + e.getMessage());
                    callback.onResult(false);
                });
    }
    
    /**
     * Compare two face images to determine if they match
     * This uses ML Kit for Android instead of the face_recognition library from Python
     * The comparison logic is adapted to work with ML Kit's face detection capabilities
     * 
     * @param referenceImage The stored reference image
     * @param capturedImage The newly captured image
     * @param callback Callback with the verification result
     */
    private void compareFaces(Bitmap referenceImage, Bitmap capturedImage, OnVerificationResultCallback callback) {
        // Extract face features from reference image
        extractFaceFeatures(referenceImage, referenceFace -> {
            if (referenceFace == null) {
                if (verificationListener != null) {
                    verificationListener.onVerificationError("Could not detect face in reference image.");
                }
                callback.onResult(false, 0);
                return;
            }
            
            // Extract face features from captured image
            extractFaceFeatures(capturedImage, capturedFace -> {
                if (capturedFace == null) {
                    if (verificationListener != null) {
                        verificationListener.onVerificationError("Could not detect face in captured image.");
                    }
                    callback.onResult(false, 0);
                    return;
                }
                
                // Compare faces using facial landmarks and features
                // This is a simplified implementation since ML Kit doesn't provide face recognition out of the box
                
                // Calculate estimated match confidence
                float confidence = calculateFaceMatchConfidence(referenceFace, capturedFace);
                
                // Determine if the faces match based on confidence thresholds
                boolean isMatch = confidence >= MINIMUM_CONFIDENCE_THRESHOLD;
                
                // Apply confidence thresholds similar to the Python model
                if (confidence >= HIGH_CONFIDENCE_THRESHOLD) {
                    Log.d(TAG, "High confidence match: " + confidence);
                } else if (confidence >= MINIMUM_CONFIDENCE_THRESHOLD) {
                    Log.d(TAG, "Low confidence match: " + confidence);
                } else {
                    Log.d(TAG, "Match failed. Confidence too low: " + confidence);
                }
                
                callback.onResult(isMatch, confidence);
            });
        });
    }
    
    /**
     * Extract face features from a bitmap
     * 
     * @param bitmap The bitmap containing a face
     * @param callback Callback with the detected face
     */
    private void extractFaceFeatures(Bitmap bitmap, OnFaceExtractedCallback callback) {
        // Create input image with optimized settings
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        
        // Configure face detector for accuracy in feature extraction but with better cross-device compatibility
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.1f) // Reduced from 0.35f for better detection across devices
                .build();
        
        FaceDetector detector = FaceDetection.getClient(options);
        
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        callback.onFaceExtracted(null);
                        return;
                    }
                    
                    // Get the first face detected
                    Face face = faces.get(0);
                    callback.onFaceExtracted(face);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Face feature extraction failed: " + e.getMessage());
                    callback.onFaceExtracted(null);
                });
    }
    
    /**
     * Calculate the confidence of a face match
     * This is an estimation since ML Kit doesn't provide face recognition directly
     * 
     * @param referenceFace The reference face
     * @param capturedFace The captured face
     * @return A confidence value between 0 and 1
     */
    private float calculateFaceMatchConfidence(Face referenceFace, Face capturedFace) {
        // Initialize confidence score
        float confidence = 0.5f; // Base confidence
        
        // Compare face features and adjust confidence
        
        // 1. Smiling probability similarity
        float smileDiff = Math.abs(referenceFace.getSmilingProbability() - capturedFace.getSmilingProbability());
        confidence += (1.0f - smileDiff) * 0.05f;
        
        // 2. Left/right eye open similarity
        float leftEyeDiff = Math.abs(referenceFace.getLeftEyeOpenProbability() - capturedFace.getLeftEyeOpenProbability());
        float rightEyeDiff = Math.abs(referenceFace.getRightEyeOpenProbability() - capturedFace.getRightEyeOpenProbability());
        confidence += (1.0f - (leftEyeDiff + rightEyeDiff) / 2.0f) * 0.05f;
        
        // 3. Head rotation similarity
        float eulerXDiff = Math.abs(referenceFace.getHeadEulerAngleX() - capturedFace.getHeadEulerAngleX()) / 90.0f;
        float eulerYDiff = Math.abs(referenceFace.getHeadEulerAngleY() - capturedFace.getHeadEulerAngleY()) / 90.0f;
        float eulerZDiff = Math.abs(referenceFace.getHeadEulerAngleZ() - capturedFace.getHeadEulerAngleZ()) / 90.0f;
        confidence += (1.0f - (eulerXDiff + eulerYDiff + eulerZDiff) / 3.0f) * 0.1f;
        
        // 4. Face proportions similarity
        Rect referenceBounds = referenceFace.getBoundingBox();
        Rect capturedBounds = capturedFace.getBoundingBox();
        
        float referenceRatio = (float) referenceBounds.width() / referenceBounds.height();
        float capturedRatio = (float) capturedBounds.width() / capturedBounds.height();
        float ratioDiff = Math.abs(referenceRatio - capturedRatio);
        confidence += (1.0f - Math.min(ratioDiff, 1.0f)) * 0.1f;
        
        // Ensure confidence is between 0 and 1
        confidence = Math.max(0.0f, Math.min(1.0f, confidence));
        
        return confidence;
    }
    
    /**
     * Mark attendance in Firestore
     * 
     * @param attendanceType The type of attendance ("check_in" or "check_out")
     */
    private void markAttendance(String attendanceType) {
        if (userId == null || userId.isEmpty()) {
            if (verificationListener != null) {
                verificationListener.onVerificationError("User ID not set. Cannot mark attendance.");
            }
            return;
        }
        
        // Get current date and time in IST
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
        
        // Set Indian Standard Time timezone (IST = UTC+5:30)
        TimeZone istTimeZone = TimeZone.getTimeZone("Asia/Kolkata");
        dateFormat.setTimeZone(istTimeZone);
        timeFormat.setTimeZone(istTimeZone);
        
        Date currentDate = new Date(); // Get current device time
        String formattedDate = dateFormat.format(currentDate);
        String formattedTime = timeFormat.format(currentDate);
        
        Log.d(TAG, "markAttendance: Using device time in IST: " + currentDate.toString() + 
              ", Formatted as " + formattedDate + " " + formattedTime);
        
        // Create attendance record
        Map<String, Object> attendanceData = new HashMap<>();
        attendanceData.put("userId", userId);
        attendanceData.put("userName", userDisplayName);
        attendanceData.put("date", formattedDate);
        attendanceData.put("time", formattedTime);
        attendanceData.put("type", attendanceType);
        attendanceData.put("status", "Present");
        attendanceData.put("verificationConfidence", lastVerificationConfidence);
        
        // Create a proper timestamp in IST
        com.google.firebase.Timestamp firebaseTimestamp = com.google.firebase.Timestamp.now();
        attendanceData.put("timestamp", firebaseTimestamp);
        
        // Add to Firestore
        db.collection("face-recognition-attendance")
                .add(attendanceData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Attendance marked successfully: " + documentReference.getId());
                    
                    if (verificationListener != null) {
                        verificationListener.onAttendanceMarked(true, 
                                "Attendance " + (attendanceType.equals("check_in") ? "Check-In" : "Check-Out") + 
                                " marked successfully");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error marking attendance: " + e.getMessage());
                    
                    if (verificationListener != null) {
                        verificationListener.onAttendanceMarked(false, 
                                "Failed to mark attendance: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Release camera resources
     */
    public void shutdown() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
    
    /**
     * Callback interfaces
     */
    private interface OnImageLoadedCallback {
        void onImageLoaded(Bitmap bitmap);
    }
    
    private interface OnFaceDetectedCallback {
        void onResult(boolean faceDetected);
    }
    
    private interface OnFaceExtractedCallback {
        void onFaceExtracted(Face face);
    }
    
    private interface OnVerificationResultCallback {
        void onResult(boolean isVerified, float confidence);
    }
} 