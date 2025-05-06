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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder> {
    private static final String TAG = "AttendanceAdapter";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    // Use a format for parsing the time from the database
    private static final SimpleDateFormat TIME_PARSER = new SimpleDateFormat("HH:mm:ss", Locale.US);
    // Use a format for displaying the time in 12-hour format
    private static final SimpleDateFormat TIME_DISPLAY_FORMAT = new SimpleDateFormat("hh:mm a", Locale.US);
    
    // Initialize timezone for India
    static {
        TimeZone istTimeZone = TimeZone.getTimeZone("Asia/Kolkata");
        DATE_FORMAT.setTimeZone(istTimeZone);
        TIME_PARSER.setTimeZone(istTimeZone);
        TIME_DISPLAY_FORMAT.setTimeZone(istTimeZone);
    }
    
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
    
    /**
     * Update Records in the adapter with better error handling
     */
    public void setRecords(List<AttendanceRecord> newRecords) {
        if (newRecords == null) {
            allRecords.clear();
            filteredRecords.clear();
            notifyDataSetChanged();
            return;
        }
        
        // Filter out null records
        List<AttendanceRecord> validRecords = new ArrayList<>();
        for (AttendanceRecord record : newRecords) {
            if (record != null) {
                validRecords.add(record);
            }
        }
        
        allRecords.clear();
        allRecords.addAll(validRecords);
        
        // By default, show all records
        filteredRecords = new ArrayList<>(allRecords);
        notifyDataSetChanged();
        
        // Reset the animation position when data changes
        lastPosition = -1;
    }
    
    /**
     * Safely filter records by type
     */
    public void filterByType(String type) {
        filteredRecords = new ArrayList<>();
        
        if (type == null || type.isEmpty() || "all".equalsIgnoreCase(type)) {
            // Show all records
            filteredRecords.addAll(allRecords);
        } else {
            // Filter by type
            for (AttendanceRecord record : allRecords) {
                if (record != null && record.getType() != null && type.equalsIgnoreCase(record.getType())) {
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
            // Set date with fallback
            String dateStr = record.getDate();
            if (dateStr != null && !dateStr.isEmpty()) {
                dateText.setText(dateStr);
            } else {
                // Try to format from timestamp if date string is missing
                Date date = record.getDateObject();
                if (date != null) {
                    dateText.setText(DATE_FORMAT.format(date));
                } else {
                    dateText.setText("Unknown date");
                }
            }
            
            // Format and set time in IST with fallback
            String timeString = record.getTime();
            try {
                if (timeString != null && !timeString.isEmpty()) {
                    // Parse the time string from the record
                    Date timeDate = TIME_PARSER.parse(timeString);
                    if (timeDate != null) {
                        // Format the time for display
                        timeText.setText(TIME_DISPLAY_FORMAT.format(timeDate));
                    } else {
                        // Fallback to original time if parsing fails
                        timeText.setText(timeString);
                    }
                } else {
                    // If no time string, try to get from timestamp
                    Date date = record.getDateObject();
                    if (date != null) {
                        timeText.setText(TIME_DISPLAY_FORMAT.format(date));
                    } else {
                        timeText.setText("--:--");
                    }
                }
            } catch (ParseException e) {
                // If parsing fails, use the original time string
                timeText.setText(timeString != null ? timeString : "--:--");
            }
            
            // Set office name with fallback
            String officeName = record.getOfficeName();
            if (officeName != null && !officeName.isEmpty()) {
                officeNameText.setText(officeName);
            } else {
                // Check if we have location ID as fallback
                String locationId = record.getLocationId();
                if (locationId != null && !locationId.isEmpty()) {
                    officeNameText.setText("Office ID: " + locationId);
                } else {
                    officeNameText.setText("Unknown Office");
                }
            }
            
            // Set type indicator (check in/out) with fallback
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
                } else {
                    // Unknown type
                    typeIndicator.setText(recordType.toUpperCase());
                    typeIndicator.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.gray_medium));
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.gray_medium));
                }
            } else {
                // No type specified
                typeIndicator.setText("UNKNOWN");
                typeIndicator.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.gray_medium));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.gray_medium));
            }
        }
    }
} 