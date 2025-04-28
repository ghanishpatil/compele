package com.example.mystartup.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mystartup.R;
import com.example.mystartup.models.OfficeLocation;

import java.util.List;

public class LocationSelectionAdapter extends RecyclerView.Adapter<LocationSelectionAdapter.LocationViewHolder> {
    private final List<OfficeLocation> locations;
    private final OnLocationSelectedListener listener;

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
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    class LocationViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView talukaTextView;

        public LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.locationNameTextView);
            talukaTextView = itemView.findViewById(R.id.locationTalukaTextView);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onLocationSelected(locations.get(position));
                }
            });
        }

        public void bind(OfficeLocation location) {
            nameTextView.setText(location.getName());
            
            String talukaText = location.getTaluka();
            if (talukaText != null && !talukaText.isEmpty()) {
                talukaTextView.setText(talukaText);
                talukaTextView.setVisibility(View.VISIBLE);
            } else {
                talukaTextView.setVisibility(View.GONE);
            }
        }
    }
} 