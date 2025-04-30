package com.example.mystartup.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mystartup.R;
import com.example.mystartup.models.OfficeLocation;

import java.util.List;

public class LocationSelectionAdapter extends RecyclerView.Adapter<LocationSelectionAdapter.LocationViewHolder> {
    private final List<OfficeLocation> locations;
    private final OnLocationSelectedListener listener;
    private static final int ANIMATION_DURATION = 300;

    public interface OnLocationSelectedListener {
        void onLocationSelected(OfficeLocation location);
    }

    public LocationSelectionAdapter(List<OfficeLocation> locations, OnLocationSelectedListener listener) {
        this.locations = locations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_location, parent, false);
        return new LocationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        OfficeLocation location = locations.get(position);
        holder.bind(location);
        
        // Add animation when item appears
        setFadeInAnimation(holder.itemView, position);
    }
    
    private void setFadeInAnimation(View itemView, int position) {
        int delay = position * 50; // stagger the animations
        itemView.setAlpha(0f);
        itemView.setTranslationY(50f);
        
        itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ANIMATION_DURATION)
                .setStartDelay(delay)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    class LocationViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView talukaTextView;
        private final TextView selectTextView;
        private final ImageView arrowIcon;
        private final View iconBackground;

        public LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.locationNameTextView);
            talukaTextView = itemView.findViewById(R.id.locationTalukaTextView);
            selectTextView = itemView.findViewById(R.id.selectTextView);
            arrowIcon = itemView.findViewById(R.id.arrowIcon);
            iconBackground = itemView.findViewById(R.id.locationIconBackground);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    // Add a pulse animation to the icon when clicked
                    animateIconPulse();
                    
                    // Call the listener after a slight delay to let the animation run
                    itemView.postDelayed(() -> {
                        listener.onLocationSelected(locations.get(position));
                    }, 200);
                }
            });
        }
        
        private void animateIconPulse() {
            // Scale up animation
            ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(iconBackground, "scaleX", 1f, 1.2f);
            ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(iconBackground, "scaleY", 1f, 1.2f);
            scaleUpX.setDuration(150);
            scaleUpY.setDuration(150);
            
            // Scale down animation
            ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(iconBackground, "scaleX", 1.2f, 1f);
            ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(iconBackground, "scaleY", 1.2f, 1f);
            scaleDownX.setDuration(150);
            scaleDownY.setDuration(150);
            scaleDownX.setStartDelay(150);
            scaleDownY.setStartDelay(150);
            
            // Start animations
            scaleUpX.start();
            scaleUpY.start();
            scaleDownX.start();
            scaleDownY.start();
            
            // Also animate the arrow
            ObjectAnimator arrowAnimator = ObjectAnimator.ofFloat(arrowIcon, "translationX", 0f, 10f, 0f);
            arrowAnimator.setDuration(300);
            arrowAnimator.start();
        }

        public void bind(OfficeLocation location) {
            nameTextView.setText(location.getName());
            
            String talukaText = location.getTaluka();
            if (talukaText != null && !talukaText.isEmpty()) {
                talukaTextView.setText(talukaText + " Taluka");
                talukaTextView.setVisibility(View.VISIBLE);
            } else {
                talukaTextView.setVisibility(View.GONE);
            }
            
            // Show the coordinates if available
            if (location.getLatitude() != null && location.getLongitude() != null) {
                String coordsText = String.format("%.6f, %.6f", location.getLatitude(), location.getLongitude());
                selectTextView.setText("Tap to select • GPS Location Available");
            }
        }
    }
} 