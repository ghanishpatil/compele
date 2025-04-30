package com.example.mystartup.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mystartup.R;
import com.example.mystartup.models.AttendanceReportItem;

import java.util.ArrayList;
import java.util.List;

public class AttendanceReportAdapter extends RecyclerView.Adapter<AttendanceReportAdapter.ViewHolder> {
    private final Context context;
    private final List<AttendanceReportItem> reportItems;
    
    public AttendanceReportAdapter(Context context) {
        this.context = context;
        this.reportItems = new ArrayList<>();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendance_report, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceReportItem item = reportItems.get(position);
        
        // Set serial number (1-based index)
        holder.serialNumberText.setText((position + 1) + ".");
        
        // Set date and office name
        holder.dateText.setText(item.getDate());
        holder.officeNameText.setText(item.getOfficeName());
        
        // Set check-in and check-out times
        holder.checkInTimeText.setText(item.getCheckInTime());
        holder.checkOutTimeText.setText(item.getCheckOutTime());
    }
    
    @Override
    public int getItemCount() {
        return reportItems.size();
    }
    
    public void setItems(List<AttendanceReportItem> items) {
        reportItems.clear();
        if (items != null) {
            reportItems.addAll(items);
        }
        notifyDataSetChanged();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView serialNumberText;
        TextView dateText;
        TextView officeNameText;
        TextView checkInTimeText;
        TextView checkOutTimeText;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            serialNumberText = itemView.findViewById(R.id.serialNumberText);
            dateText = itemView.findViewById(R.id.dateText);
            officeNameText = itemView.findViewById(R.id.officeNameText);
            checkInTimeText = itemView.findViewById(R.id.checkInTimeText);
            checkOutTimeText = itemView.findViewById(R.id.checkOutTimeText);
        }
    }
} 