package com.example.mystartup;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;

/**
 * Simple test class to verify CameraX imports are working.
 * This class does nothing, it's just to check if the imports resolve correctly.
 */
public class CameraXTest {
    private CameraSelector cameraSelector;
    private ImageCapture imageCapture;
    private Preview preview;
    private ProcessCameraProvider cameraProvider;
    
    public CameraXTest() {
        // Empty constructor, this class is just for import testing
    }
} 