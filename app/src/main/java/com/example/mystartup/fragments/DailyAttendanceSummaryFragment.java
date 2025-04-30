package com.example.mystartup.fragments;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.mystartup.R;
import com.example.mystartup.databinding.FragmentDailyAttendanceSummaryBinding;
import com.example.mystartup.models.AttendanceRecord;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DailyAttendanceSummaryFragment extends Fragment {
    private static final String TAG = "DailyAttendanceSummary";
    private FragmentDailyAttendanceSummaryBinding binding;
    private Calendar calendar;
    private SimpleDateFormat displayDateFormatter;
    private SimpleDateFormat apiDateFormatter;
    private FirebaseFirestore db;
    private int colorEven;
    private int colorOdd;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calendar = Calendar.getInstance();
        displayDateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        apiDateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDailyAttendanceSummaryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            // Initialize row colors for alternating table rows
            colorEven = ContextCompat.getColor(requireContext(), R.color.tableRowEven);
            colorOdd = ContextCompat.getColor(requireContext(), R.color.tableRowOdd);
            
        setupDatePicker();
        setupGenerateReportButton();
            setupReportTable();
            
            // Set today's date as default
            String today = displayDateFormatter.format(calendar.getTime());
            binding.dateInput.setText(today);
            
            // Hide results initially
            if (binding.resultsContainer != null) {
                binding.resultsContainer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
            Toast.makeText(requireContext(), "Error initializing report screen: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupReportTable() {
        try {
            // Ensure the table layout exists in the binding
            if (binding.attendanceTable == null) {
                Log.e(TAG, "Table layout not found in the binding");
                return;
            }
            
            // Create table header
            TableRow headerRow = new TableRow(requireContext());
            headerRow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary));
            headerRow.setPadding(2, 2, 2, 2);
            
            // Add header columns
            headerRow.addView(createHeaderCell("Sr. No."));
            headerRow.addView(createHeaderCell("Sevarth ID"));
            headerRow.addView(createHeaderCell("Name"));
            headerRow.addView(createHeaderCell("Location"));
            headerRow.addView(createHeaderCell("Check-in"));
            headerRow.addView(createHeaderCell("Check-out"));
            
            binding.attendanceTable.addView(headerRow);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up report table", e);
        }
    }
    
    private TextView createHeaderCell(String text) {
        TextView cell = new TextView(requireContext());
        cell.setText(text);
        cell.setPadding(12, 12, 12, 12);
        cell.setTextColor(Color.WHITE);
        cell.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary));
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setTypeface(null, android.graphics.Typeface.BOLD);
        
        // Set layout parameters with margins for borders
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(1, 1, 1, 1);
        cell.setLayoutParams(params);
        
        return cell;
    }

    private void setupDatePicker() {
        binding.dateInput.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
            requireContext(),
            (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                binding.dateInput.setText(displayDateFormatter.format(calendar.getTime()));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void setupGenerateReportButton() {
        binding.generateReportButton.setOnClickListener(v -> {
            String selectedDisplayDate = binding.dateInput.getText().toString();
            
            if (selectedDisplayDate.isEmpty()) {
                Toast.makeText(requireContext(), "Please select a date", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // Convert to API date format (yyyy-MM-dd)
                Date date = displayDateFormatter.parse(selectedDisplayDate);
                if (date != null) {
                    String apiDate = apiDateFormatter.format(date);
                    generateReport(apiDate);
                } else {
                    Toast.makeText(requireContext(), "Invalid date format", Toast.LENGTH_SHORT).show();
                }
            } catch (ParseException e) {
                Log.e(TAG, "Date parsing error", e);
                Toast.makeText(requireContext(), "Invalid date format", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void generateReport(String date) {
        Log.d(TAG, "Generating report for date: " + date);
        
        if (binding.progressBar != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }
        
        if (binding.resultsContainer != null) {
            binding.resultsContainer.setVisibility(View.GONE);
        }
        
        // Clear existing table rows except header
        clearTableRows();
        
        // Query Firestore for all attendance records for the selected date
        db.collection("face-recognition-attendance")
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                Log.d(TAG, "Query successful. Got " + queryDocumentSnapshots.size() + " attendance records");
                
                try {
                    // Group records by user (sevarthId or userId) to associate check-ins with check-outs
                    Map<String, Map<String, AttendanceRecord>> userRecords = new HashMap<>();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            String userId = document.getString("userId");
                            String sevarthId = document.getString("sevarthId");
                            String type = document.getString("type");
                            
                            if (type == null) {
                                continue; // Skip records without a type
                            }
                            
                            // Use sevarthId as primary key, fall back to userId if not available
                            String userKey = (sevarthId != null && !sevarthId.isEmpty()) ? sevarthId : userId;
                            
                            if (userKey == null || userKey.isEmpty()) {
                                Log.w(TAG, "Record without valid user identifier: " + document.getId());
                                continue;
                            }
                            
                            // Get or create the user's records map
                            Map<String, AttendanceRecord> recordsMap = userRecords.get(userKey);
                            if (recordsMap == null) {
                                recordsMap = new HashMap<>();
                                userRecords.put(userKey, recordsMap);
                            }
                            
                            // Create attendance record
                            AttendanceRecord record = new AttendanceRecord();
                            record.setId(document.getId());
                            record.setDate(document.getString("date"));
                            record.setUserId(userId);
                            record.setUserName(document.getString("userName"));
                            record.setSevarthId(sevarthId);
                            
                            // Get office name with fallbacks for different field names
                            String officeName = document.getString("officeName");
                            if (officeName == null || officeName.isEmpty()) {
                                officeName = document.getString("locationName");
                            }
                            if (officeName == null || officeName.isEmpty()) {
                                officeName = document.getString("office_name");
                            }
                            if (officeName == null || officeName.isEmpty()) {
                                officeName = "Unknown Location";
                            }
                            record.setOfficeName(officeName);
                            
                            record.setTime(document.getString("time"));
                            record.setType(type);
                            
                            // Store by type (check_in or check_out)
                            recordsMap.put(type, record);
                            
                            Log.d(TAG, "Processed record: " + userKey + ", type=" + type + ", location=" + officeName);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing record: " + document.getId(), e);
                        }
                    }
                    
                    // Convert to a list of data objects for the table
                    List<AttendanceTableRow> tableData = new ArrayList<>();
                    
                    for (Map.Entry<String, Map<String, AttendanceRecord>> entry : userRecords.entrySet()) {
                        Map<String, AttendanceRecord> records = entry.getValue();
                        
                        // Get check-in and check-out records
                        AttendanceRecord checkIn = records.get("check_in");
                        AttendanceRecord checkOut = records.get("check_out");
                        
                        // Use either record for user details, preferring check-in
                        AttendanceRecord primaryRecord = checkIn != null ? checkIn : checkOut;
                        
                        if (primaryRecord != null) {
                            AttendanceTableRow tableRow = new AttendanceTableRow();
                            tableRow.sevarthId = primaryRecord.getSevarthId() != null ? primaryRecord.getSevarthId() : "";
                            tableRow.userName = primaryRecord.getUserName() != null ? primaryRecord.getUserName() : "";
                            tableRow.location = primaryRecord.getOfficeName() != null ? primaryRecord.getOfficeName() : "";
                            tableRow.checkInTime = checkIn != null ? checkIn.getTime() : "-";
                            tableRow.checkOutTime = checkOut != null ? checkOut.getTime() : "-";
                            
                            tableData.add(tableRow);
                        }
                    }
                    
                    // Sort alphabetically by name
                    Collections.sort(tableData, (a, b) -> a.userName.compareToIgnoreCase(b.userName));
                    
                    // Add data to the table
                    if (tableData.isEmpty()) {
                        if (binding.noRecordsText != null) {
                            binding.noRecordsText.setVisibility(View.VISIBLE);
                        }
                        if (binding.attendanceTable != null) {
                            binding.attendanceTable.setVisibility(View.GONE);
                        }
                        
                        Toast.makeText(requireContext(), "No attendance records found for this date", Toast.LENGTH_SHORT).show();
                    } else {
                        if (binding.noRecordsText != null) {
                            binding.noRecordsText.setVisibility(View.GONE);
                        }
                        if (binding.attendanceTable != null) {
                            binding.attendanceTable.setVisibility(View.VISIBLE);
                        }
                        
                        // Add rows to the table
                        for (int i = 0; i < tableData.size(); i++) {
                            AttendanceTableRow data = tableData.get(i);
                            addTableRow(i + 1, data, i % 2 == 0);
                        }
                        
                        // Show success toast
                        Toast.makeText(requireContext(), 
                            "Showing " + tableData.size() + " attendance records for selected date", 
                            Toast.LENGTH_SHORT).show();
                    }
                    
                    // Show results container
                    if (binding.resultsContainer != null) {
                        binding.resultsContainer.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing attendance data", e);
                    Toast.makeText(requireContext(), "Error processing attendance data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                } finally {
                    if (binding.progressBar != null) {
                        binding.progressBar.setVisibility(View.GONE);
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching attendance records", e);
                Toast.makeText(requireContext(), "Error fetching attendance records: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                
                if (binding.progressBar != null) {
                    binding.progressBar.setVisibility(View.GONE);
                }
                
                // Try alternative approach with a different field name
                tryAlternativeQuery(date);
            });
    }
    
    private void tryAlternativeQuery(String date) {
        Log.d(TAG, "Trying alternative query for date: " + date);
        
        // Query with a different date field name
        db.collection("face-recognition-attendance")
            .whereEqualTo("attendanceDate", date)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (queryDocumentSnapshots.size() > 0) {
                    Log.d(TAG, "Alternative query successful. Got " + queryDocumentSnapshots.size() + " records");
                    processQueryResults(queryDocumentSnapshots);
                } else {
                    Log.w(TAG, "No records found with alternative query");
                    
                    if (binding.progressBar != null) {
                        binding.progressBar.setVisibility(View.GONE);
                    }
                    
                    if (binding.noRecordsText != null) {
                        binding.noRecordsText.setVisibility(View.VISIBLE);
                    }
                    
                    if (binding.resultsContainer != null) {
                        binding.resultsContainer.setVisibility(View.VISIBLE);
                    }
                    
                    Toast.makeText(requireContext(), "No attendance records found for this date", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error with alternative query", e);
                
                if (binding.progressBar != null) {
                    binding.progressBar.setVisibility(View.GONE);
                }
                
                if (binding.noRecordsText != null) {
                    binding.noRecordsText.setVisibility(View.VISIBLE);
                }
                
                if (binding.resultsContainer != null) {
                    binding.resultsContainer.setVisibility(View.VISIBLE);
                }
                
                Toast.makeText(requireContext(), "No attendance records found for this date", Toast.LENGTH_SHORT).show();
            });
    }
    
    private void processQueryResults(com.google.firebase.firestore.QuerySnapshot queryDocumentSnapshots) {
        try {
            // Group records by user (sevarthId or userId) to associate check-ins with check-outs
            Map<String, Map<String, AttendanceRecord>> userRecords = new HashMap<>();
            
            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                try {
                    String userId = document.getString("userId");
                    String sevarthId = document.getString("sevarthId");
                    String type = document.getString("type");
                    
                    if (type == null) {
                        continue; // Skip records without a type
                    }
                    
                    // Use sevarthId as primary key, fall back to userId if not available
                    String userKey = (sevarthId != null && !sevarthId.isEmpty()) ? sevarthId : userId;
                    
                    if (userKey == null || userKey.isEmpty()) {
                        Log.w(TAG, "Record without valid user identifier: " + document.getId());
                        continue;
                    }
                    
                    // Get or create the user's records map
                    Map<String, AttendanceRecord> recordsMap = userRecords.get(userKey);
                    if (recordsMap == null) {
                        recordsMap = new HashMap<>();
                        userRecords.put(userKey, recordsMap);
                    }
                    
                    // Create attendance record
                    AttendanceRecord record = new AttendanceRecord();
                    record.setId(document.getId());
                    record.setDate(document.getString("date"));
                    record.setUserId(userId);
                    record.setUserName(document.getString("userName"));
                    record.setSevarthId(sevarthId);
                    
                    // Get office name with fallbacks for different field names
                    String officeName = document.getString("officeName");
                    if (officeName == null || officeName.isEmpty()) {
                        officeName = document.getString("locationName");
                    }
                    if (officeName == null || officeName.isEmpty()) {
                        officeName = document.getString("office_name");
                    }
                    if (officeName == null || officeName.isEmpty()) {
                        officeName = "Unknown Location";
                    }
                    record.setOfficeName(officeName);
                    
                    record.setTime(document.getString("time"));
                    record.setType(type);
                    
                    // Store by type (check_in or check_out)
                    recordsMap.put(type, record);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing record: " + document.getId(), e);
                }
            }
            
            // Convert to a list of data objects for the table
            List<AttendanceTableRow> tableData = new ArrayList<>();
            
            for (Map.Entry<String, Map<String, AttendanceRecord>> entry : userRecords.entrySet()) {
                Map<String, AttendanceRecord> records = entry.getValue();
                
                // Get check-in and check-out records
                AttendanceRecord checkIn = records.get("check_in");
                AttendanceRecord checkOut = records.get("check_out");
                
                // Use either record for user details, preferring check-in
                AttendanceRecord primaryRecord = checkIn != null ? checkIn : checkOut;
                
                if (primaryRecord != null) {
                    AttendanceTableRow tableRow = new AttendanceTableRow();
                    tableRow.sevarthId = primaryRecord.getSevarthId() != null ? primaryRecord.getSevarthId() : "";
                    tableRow.userName = primaryRecord.getUserName() != null ? primaryRecord.getUserName() : "";
                    tableRow.location = primaryRecord.getOfficeName() != null ? primaryRecord.getOfficeName() : "";
                    tableRow.checkInTime = checkIn != null ? checkIn.getTime() : "-";
                    tableRow.checkOutTime = checkOut != null ? checkOut.getTime() : "-";
                    
                    tableData.add(tableRow);
                }
            }
            
            // Sort alphabetically by name
            Collections.sort(tableData, (a, b) -> a.userName.compareToIgnoreCase(b.userName));
            
            // Add data to the table
            if (tableData.isEmpty()) {
                if (binding.noRecordsText != null) {
                    binding.noRecordsText.setVisibility(View.VISIBLE);
                }
                if (binding.attendanceTable != null) {
                    binding.attendanceTable.setVisibility(View.GONE);
                }
                
                Toast.makeText(requireContext(), "No attendance records found for this date", Toast.LENGTH_SHORT).show();
            } else {
                if (binding.noRecordsText != null) {
                    binding.noRecordsText.setVisibility(View.GONE);
                }
                if (binding.attendanceTable != null) {
                    binding.attendanceTable.setVisibility(View.VISIBLE);
                }
                
                // Clear existing rows except header
                clearTableRows();
                
                // Add rows to the table
                for (int i = 0; i < tableData.size(); i++) {
                    AttendanceTableRow data = tableData.get(i);
                    addTableRow(i + 1, data, i % 2 == 0);
                }
                
                // Show success toast
                Toast.makeText(requireContext(), 
                    "Showing " + tableData.size() + " attendance records for selected date", 
                    Toast.LENGTH_SHORT).show();
            }
            
            // Show results container
            if (binding.resultsContainer != null) {
                binding.resultsContainer.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing alternative query results", e);
            Toast.makeText(requireContext(), "Error processing attendance data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(View.GONE);
            }
        }
    }
    
    // Helper class to store table row data
    private static class AttendanceTableRow {
        String sevarthId;
        String userName;
        String location;
        String checkInTime;
        String checkOutTime;
    }
    
    // Clear all rows from the table except the header row
    private void clearTableRows() {
        if (binding.attendanceTable != null && binding.attendanceTable.getChildCount() > 1) {
            binding.attendanceTable.removeViews(1, binding.attendanceTable.getChildCount() - 1);
        }
    }
    
    // Add a row to the table
    private void addTableRow(int serialNumber, AttendanceTableRow data, boolean isEvenRow) {
        if (binding.attendanceTable == null) return;
        
        try {
            // Create a new row
            TableRow row = new TableRow(requireContext());
            row.setPadding(0, 0, 0, 0);
            
            // Set row layout parameters
            TableLayout.LayoutParams rowParams = new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            );
            row.setLayoutParams(rowParams);
            
            // Background color for alternating rows
            int bgColor = isEvenRow ? colorEven : colorOdd;
            
            // Create cells
            TextView serialCell = createTableCell(String.valueOf(serialNumber), bgColor);
            TextView sevarthIdCell = createTableCell(data.sevarthId, bgColor);
            TextView nameCell = createTableCell(data.userName, bgColor);
            TextView locationCell = createTableCell(data.location, bgColor);
            TextView checkInCell = createTableCell(data.checkInTime, bgColor);
            TextView checkOutCell = createTableCell(data.checkOutTime, bgColor);
            
            // Set column widths
            serialCell.setMinWidth(60);
            sevarthIdCell.setMinWidth(120);
            nameCell.setMinWidth(180);
            locationCell.setMinWidth(150);
            checkInCell.setMinWidth(100);
            checkOutCell.setMinWidth(100);
            
            // Add cells to row
            row.addView(serialCell);
            row.addView(sevarthIdCell);
            row.addView(nameCell);
            row.addView(locationCell);
            row.addView(checkInCell);
            row.addView(checkOutCell);
            
            // Add row to table
            binding.attendanceTable.addView(row);
        } catch (Exception e) {
            Log.e(TAG, "Error adding table row", e);
        }
    }
    
    // Create a table cell
    private TextView createTableCell(String text, int backgroundColor) {
        TextView cell = new TextView(requireContext());
        cell.setText(text != null ? text : "");
        cell.setPadding(12, 12, 12, 12);
        cell.setBackgroundColor(backgroundColor);
        cell.setGravity(android.view.Gravity.CENTER);
        
        // Set layout parameters with margins for borders
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(1, 1, 1, 1);
        cell.setLayoutParams(params);
        
        return cell;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 