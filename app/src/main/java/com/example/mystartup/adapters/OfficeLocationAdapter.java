package com.example.mystartup.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mystartup.R;
import com.example.mystartup.models.OfficeLocation;

import java.util.List;

public class OfficeLocationAdapter extends RecyclerView.Adapter<OfficeLocationAdapter.OfficeLocationViewHolder> {
    
    private List<OfficeLocation> locationList;
    private OnOfficeLocationClickListener listener;
    
    public interface OnOfficeLocationClickListener {
        void onEditClick(OfficeLocation location);
        void onDeleteClick(OfficeLocation location);
    }
    
    public OfficeLocationAdapter(List<OfficeLocation> locationList, OnOfficeLocationClickListener listener) {
        this.locationList = locationList;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public OfficeLocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_office_location, parent, false);
        return new OfficeLocationViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull OfficeLocationViewHolder holder, int position) {
        OfficeLocation location = locationList.get(position);
        holder.nameTextView.setText(location.getName());
        holder.talukaTextView.setText(location.getTaluka());
        holder.coordinatesTextView.setText(location.getFormattedCoordinates());
        holder.radiusTextView.setText("Radius: " + location.getRadius() + "m");
        
        holder.editButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(location);
            }
        });
        
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(location);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return locationList.size();
    }
    
    static class OfficeLocationViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        TextView talukaTextView;
        TextView coordinatesTextView;
        TextView radiusTextView;
        ImageButton editButton;
        ImageButton deleteButton;
        
        public OfficeLocationViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.locationNameTextView);
            talukaTextView = itemView.findViewById(R.id.talukaTextView);
            coordinatesTextView = itemView.findViewById(R.id.coordinatesTextView);
            radiusTextView = itemView.findViewById(R.id.radiusTextView);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
} 