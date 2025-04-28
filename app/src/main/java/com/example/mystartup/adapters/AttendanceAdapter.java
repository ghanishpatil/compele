package com.example.mystartup.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.US);
    
    private final Context context;
    private final List<AttendanceRecord> allRecords;
    private List<AttendanceRecord> filteredRecords;
    
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
    }
    
    class AttendanceViewHolder extends RecyclerView.ViewHolder {
        private final TextView attendanceTypeText;
        private final TextView dateTimeText;
        private final TextView locationText;
        private final TextView faceConfidenceText;
        
        public AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            attendanceTypeText = itemView.findViewById(R.id.attendanceTypeText);
            dateTimeText = itemView.findViewById(R.id.dateTimeText);
            locationText = itemView.findViewById(R.id.locationText);
            faceConfidenceText = itemView.findViewById(R.id.faceConfidenceText);
        }
        
        public void bind(AttendanceRecord record) {
            // Set attendance type with appropriate background color
            if (record.isCheckIn()) {
                attendanceTypeText.setText("CHECK-IN");
                attendanceTypeText.setBackgroundTintList(
                        ContextCompat.getColorStateList(context, R.color.green_success));
            } else {
                attendanceTypeText.setText("CHECK-OUT");
                attendanceTypeText.setBackgroundTintList(
                        ContextCompat.getColorStateList(context, R.color.blue_primary));
            }
            
            // Format and set date/time - use the formatted date and time from record if available
            if (record.getDate() != null && record.getTime() != null) {
                dateTimeText.setText(record.getDate() + " " + record.getTime());
            } else if (record.getTimestamp() != null) {
                dateTimeText.setText(DATE_FORMAT.format(record.getTimestamp().toDate()));
            } else {
                dateTimeText.setText("Unknown Time");
            }
            
            // Set location or username if location isn't available
            if (record.getLocationName() != null && !record.getLocationName().isEmpty()) {
                locationText.setText(record.getLocationName());
            } else if (record.getUserName() != null && !record.getUserName().isEmpty()) {
                locationText.setText("User: " + record.getUserName());
            } else {
                locationText.setText("Unknown Location");
            }
            
            // Set face confidence - use verificationConfidence instead of faceConfidence
            int confidencePercent = (int) (record.getVerificationConfidence() * 100);
            faceConfidenceText.setText("Verification Confidence: " + confidencePercent + "%");
        }
    }
} 