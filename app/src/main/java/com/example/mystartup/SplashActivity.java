package com.example.mystartup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 2000; // 2 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        // Initialize views
        ImageView logoImageView = findViewById(R.id.splash_logo);
        TextView titleTextView = findViewById(R.id.splash_title);
        TextView subtitleTextView = findViewById(R.id.splash_subtitle);
        
        // Load animations
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        
        // Start animations
        logoImageView.startAnimation(fadeIn);
        titleTextView.startAnimation(slideUp);
        subtitleTextView.startAnimation(slideUp);
        
        // Navigate to LoginActivity after delay
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            startActivity(intent);
            
            // Apply transition animation
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            
            // Close this activity
            finish();
        }, SPLASH_DURATION);
    }
} 