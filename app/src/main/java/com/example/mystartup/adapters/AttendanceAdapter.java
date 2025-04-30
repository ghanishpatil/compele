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
        private final TextView dateText;
        private final TextView timeText;
        private final TextView officeNameText;
        
        public AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            timeText = itemView.findViewById(R.id.timeText);
            officeNameText = itemView.findViewById(R.id.officeNameText);
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
        }
    }
} 