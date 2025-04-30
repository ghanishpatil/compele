package com.example.mystartup.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mystartup.R;
import com.example.mystartup.models.AttendanceRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.US);
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("hh:mm a", Locale.US);
    
    private final Context context;
    private final List<AttendanceRecord> allRecords;
    private List<AttendanceRecord> filteredRecords;
    private int lastPosition = -1;
    
    public AttendanceAdapter(Context context) {
        this.context = context;
        this.allRecords = new ArrayList<>();
        this.filteredRecords = new ArrayList<>();
    }
    
    @NonNull
    @Override
    public AttendanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendance_record, parent, false);
        return new AttendanceViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull AttendanceViewHolder holder, int position) {
        AttendanceRecord record = filteredRecords.get(position);
        holder.bind(record);
        
        // Apply animation to each item
        setAnimation(holder.itemView, position);
    }
    
    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            viewToAnimate.setAlpha(0f);
            viewToAnimate.animate().alpha(1f).setDuration(300).start();
            lastPosition = position;
        }
    }
    
    @Override
    public int getItemCount() {
        return filteredRecords.size();
    }
    
    public void setRecords(List<AttendanceRecord> newRecords) {
        allRecords.clear();
        allRecords.addAll(newRecords);
        
        // By default, show all records
        filteredRecords = new ArrayList<>(allRecords);
        notifyDataSetChanged();
        
        // Reset the animation position when data changes
        lastPosition = -1;
    }
    
    public void filterByType(String type) {
        filteredRecords = new ArrayList<>();
        
        if (type == null || type.isEmpty() || "all".equalsIgnoreCase(type)) {
            // Show all records
            filteredRecords.addAll(allRecords);
        } else {
            // Filter by type
            for (AttendanceRecord record : allRecords) {
                if (type.equalsIgnoreCase(record.getType())) {
                    filteredRecords.add(record);
                }
            }
        }
        
        notifyDataSetChanged();
        
        // Reset the animation position when filter changes
        lastPosition = -1;
    }
    
    class AttendanceViewHolder extends RecyclerView.ViewHolder {
        private final TextView dateText;
        private final TextView timeText;
        private final TextView officeNameText;
        private final TextView typeIndicator;
        private final View statusIndicator;
        
        public AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            timeText = itemView.findViewById(R.id.timeText);
            officeNameText = itemView.findViewById(R.id.officeNameText);
            typeIndicator = itemView.findViewById(R.id.typeIndicator);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            
            // Apply ripple effect on click
            itemView.setOnClickListener(v -> {
                v.startAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in));
            });
        }
        
        public void bind(AttendanceRecord record) {
            // Set date
            dateText.setText(record.getDate());
            
            // Set time
            timeText.setText(record.getTime());
            
            // Set office name
            String officeName = record.getOfficeName();
            if (officeName != null && !officeName.isEmpty()) {
                officeNameText.setText(officeName);
            } else {
                officeNameText.setText("Unknown Office");
            }
            
            // Set type indicator (check in/out)
            String recordType = record.getType();
            if (recordType != null) {
                if (recordType.equalsIgnoreCase("check_in")) {
                    typeIndicator.setText("CHECK IN");
                    typeIndicator.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.success_green));
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.success_green));
                } else if (recordType.equalsIgnoreCase("check_out")) {
                    typeIndicator.setText("CHECK OUT");
                    typeIndicator.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.navy_medium));
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.navy_medium));
                }
            }
        }
    }
} 