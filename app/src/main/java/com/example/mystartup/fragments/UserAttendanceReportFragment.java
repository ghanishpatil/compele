package com.example.mystartup.fragments;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mystartup.R;
import com.example.mystartup.adapters.AttendanceAdapter;
import com.example.mystartup.adapters.AttendanceReportAdapter;
import com.example.mystartup.databinding.FragmentUserAttendanceReportBinding;
import com.example.mystartup.models.AttendanceRecord;
import com.example.mystartup.models.AttendanceReportItem;
import com.example.mystartup.models.User;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserAttendanceReportFragment extends Fragment {
    private static final String TAG = "UserAttendanceReport";
    private FragmentUserAttendanceReportBinding binding;
    private Calendar calendar;
    private SimpleDateFormat dateFormatter;
    private SimpleDateFormat apiDateFormatter;
    private List<User> userList;
    private Map<String, String> userDisplayToId;
    private FirebaseFirestore db;
    private AttendanceAdapter attendanceAdapter;
    private AttendanceReportAdapter attendanceReportAdapter;
    // Table row alternating colors
    private int colorEven;
    private int colorOdd;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calendar = Calendar.getInstance();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        apiDateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        userList = new ArrayList<>();
        userDisplayToId = new HashMap<>();
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
        binding = FragmentUserAttendanceReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout", e);
            Toast.makeText(requireContext(), "Error loading report screen: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            // Fallback to a simple view if binding fails
            return new View(requireContext());
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            // Initialize row colors for alternating table rows
            colorEven = ContextCompat.getColor(requireContext(), R.color.tableRowEven);
            colorOdd = ContextCompat.getColor(requireContext(), R.color.tableRowOdd);
            
            // Set up RecyclerView for results (keeping as a fallback)
            attendanceAdapter = new AttendanceAdapter(requireContext());
            
            // Initialize the report adapter (keeping as a fallback)
            attendanceReportAdapter = new AttendanceReportAdapter(requireContext());
            
            binding.attendanceRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            binding.attendanceRecyclerView.setAdapter(attendanceReportAdapter);
            
            // Hide the results initially
            binding.resultsContainer.setVisibility(View.GONE);
            
            // Fetch users and set up form
            fetchUsers();
        setupDatePickers();
        setupGenerateReportButton();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
            Toast.makeText(requireContext(), "Error initializing report screen: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchUsers() {
        if (binding == null) return;
        
        binding.progressBar.setVisibility(View.VISIBLE);
        
        try {
            db.collection("users")
                .whereEqualTo("role", "user")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (binding == null) return; // Check if binding still exists
                    
                    userList.clear();
                    userDisplayToId.clear();
                    List<String> userDisplayNames = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            User user = document.toObject(User.class);
                            if (user != null && user.isActive()) {
                                userList.add(user);
                                // Make sure getFullName() doesn't return null by using safe concatenation
                                String firstName = user.getFirstName() != null ? user.getFirstName() : "";
                                String lastName = user.getLastName() != null ? user.getLastName() : "";
                                String fullName = firstName + " " + lastName;
                                String sevarthId = user.getSevarthId() != null ? user.getSevarthId() : "";
                                
                                String displayName = fullName + " (" + sevarthId + ")";
                                userDisplayNames.add(displayName);
                                userDisplayToId.put(displayName, sevarthId);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing user document", e);
                        }
                    }
                    
                    setupUserDropdown(userDisplayNames);
                    binding.progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    if (binding == null) return;
                    
                    Log.e(TAG, "Error fetching users", e);
                    Toast.makeText(requireContext(), "Failed to load users: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.progressBar.setVisibility(View.GONE);
                });
        } catch (Exception e) {
            Log.e(TAG, "Error in fetchUsers", e);
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    private void setupUserDropdown(List<String> userDisplayNames) {
        if (binding == null) return;
        
        try {
            if (userDisplayNames.isEmpty()) {
                userDisplayNames.add("No users found");
            }
            
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
                userDisplayNames
        );
            
        binding.userDropdown.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up user dropdown", e);
        }
    }

    private void setupDatePickers() {
        if (binding == null) return;
        
        try {
        binding.startDateEdit.setOnClickListener(v -> showDatePicker(binding.startDateEdit));
        binding.endDateEdit.setOnClickListener(v -> showDatePicker(binding.endDateEdit));
        } catch (Exception e) {
            Log.e(TAG, "Error setting up date pickers", e);
        }
    }

    private void showDatePicker(TextInputEditText dateEdit) {
        DatePickerDialog dialog = new DatePickerDialog(
            requireContext(),
            (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                dateEdit.setText(dateFormatter.format(calendar.getTime()));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void setupGenerateReportButton() {
        if (binding == null) return;
        
        try {
        binding.generateReportButton.setOnClickListener(v -> {
                String selectedUserDisplay = binding.userDropdown.getText().toString();
                String startDateStr = binding.startDateEdit.getText().toString();
                String endDateStr = binding.endDateEdit.getText().toString();

                if (selectedUserDisplay.isEmpty() || startDateStr.isEmpty() || endDateStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

                if (!userDisplayToId.containsKey(selectedUserDisplay)) {
                    Toast.makeText(requireContext(), "Please select a valid user", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                String sevarthId = userDisplayToId.get(selectedUserDisplay);
                
                // Convert dates to API format (yyyy-MM-dd)
                String apiStartDate = "";
                String apiEndDate = "";
                try {
                    Date startDate = dateFormatter.parse(startDateStr);
                    Date endDate = dateFormatter.parse(endDateStr);
                    
                    if (startDate != null && endDate != null) {
                        if (startDate.after(endDate)) {
                            Toast.makeText(requireContext(), "Start date cannot be after end date", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        apiStartDate = apiDateFormatter.format(startDate);
                        apiEndDate = apiDateFormatter.format(endDate);
                    }
                } catch (ParseException e) {
                    Log.e(TAG, "Date parsing error", e);
                    Toast.makeText(requireContext(), "Invalid date format", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Generate report
                generateReport(sevarthId, apiStartDate, apiEndDate);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up generate button", e);
        }
    }

    private void generateReport(String sevarthId, String startDate, String endDate) {
        if (binding == null) return;
        
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.resultsContainer.setVisibility(View.GONE);
        
        try {
            // Query Firestore for attendance records
            db.collection("face-recognition-attendance")
                .whereEqualTo("sevarthId", sevarthId)
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (binding == null) return;
                    
                    try {
                        List<AttendanceRecord> records = new ArrayList<>();
                        
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            try {
                                Log.d(TAG, "Document data: " + document.getData());
                                
                                // Create attendance record from document
                                AttendanceRecord record = new AttendanceRecord();
                                record.setId(document.getId());
                                record.setDate(document.getString("date"));
                                record.setStatus(document.getString("status"));
                                record.setTime(document.getString("time"));
                                record.setTimestamp(document.getTimestamp("timestamp"));
                                record.setType(document.getString("type"));
                                record.setUserId(document.getString("userId"));
                                record.setUserName(document.getString("userName"));
                                record.setSevarthId(document.getString("sevarthId"));
                                
                                // Get office name
                                String officeName = document.getString("officeName");
                                if (officeName == null || officeName.isEmpty()) {
                                    officeName = "Unknown Office";
                                }
                                record.setOfficeName(officeName);
                                
                                // Verification confidence
                                if (document.contains("verificationConfidence")) {
                                    Double confidence = document.getDouble("verificationConfidence");
                                    if (confidence != null) {
                                        record.setVerificationConfidence(confidence);
                                    }
                                }
                                
                                records.add(record);
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing attendance record", e);
                            }
                        }
                        
                        // Group records by date and office to create the report items
                        Map<String, AttendanceReportItem> reportItemMap = new HashMap<>();
                        
                        for (AttendanceRecord record : records) {
                            String key = record.getDate() + "_" + record.getOfficeName();
                            AttendanceReportItem reportItem = reportItemMap.get(key);
                            
                            if (reportItem == null) {
                                reportItem = new AttendanceReportItem(record.getDate(), record.getOfficeName());
                                reportItemMap.put(key, reportItem);
                            }
                            
                            // Set check-in or check-out time based on record type
                            if ("check_in".equals(record.getType())) {
                                reportItem.setCheckInTime(record.getTime());
                            } else if ("check_out".equals(record.getType())) {
                                reportItem.setCheckOutTime(record.getTime());
                            }
                        }
                        
                        // Convert map values to a list and sort by date
                        List<AttendanceReportItem> reportItems = new ArrayList<>(reportItemMap.values());
                        reportItems.sort((a, b) -> a.getDate().compareTo(b.getDate()));
                        
                        // Update UI
                        if (reportItems.isEmpty()) {
                            binding.noRecordsText.setVisibility(View.VISIBLE);
                            binding.attendanceTable.setVisibility(View.GONE);
                        } else {
                            binding.noRecordsText.setVisibility(View.GONE);
                            binding.attendanceTable.setVisibility(View.VISIBLE);
                            
                            // Clear the table except for the header row
                            clearTableRows();
                            
                            // Populate table with the data
                            populateTable(reportItems);
                        }
                        
                        // Show results
                        binding.resultsContainer.setVisibility(View.VISIBLE);
                        binding.progressBar.setVisibility(View.GONE);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing attendance records in main query", e);
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Error generating report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (binding == null) return;
                    
                    Log.e(TAG, "Error fetching attendance records", e);
                    
                    String errorMessage;
                    if (e.getMessage() != null && e.getMessage().contains("FAILED_PRECONDITION")) {
                        errorMessage = "This query requires a Firestore index. Please check Firebase console and create the required index.";
                        
                        // For development: Try a simpler query as a fallback that doesn't need an index
                        try {
                            // Fallback to using only the sevarthId filter
                            tryAlternativeQuery(sevarthId, startDate, endDate);
                            return;
                        } catch (Exception ex) {
                            Log.e(TAG, "Error with alternative query", ex);
                        }
                    } else {
                        errorMessage = "Failed to generate report: " + e.getMessage();
                    }
                    
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                    binding.progressBar.setVisibility(View.GONE);
                });
        } catch (Exception e) {
            Log.e(TAG, "Error in generateReport", e);
            binding.progressBar.setVisibility(View.GONE);
        }
    }
    
    // Clear all rows from the table except the header row
    private void clearTableRows() {
        TableLayout table = binding.attendanceTable;
        if (table.getChildCount() > 1) {
            table.removeViews(1, table.getChildCount() - 1);
        }
    }
    
    // Populate the table with attendance report data
    private void populateTable(List<AttendanceReportItem> items) {
        TableLayout table = binding.attendanceTable;
        
        for (int i = 0; i < items.size(); i++) {
            AttendanceReportItem item = items.get(i);
            
            // Create a new row
            TableRow row = new TableRow(requireContext());
            row.setPadding(0, 0, 0, 0);
            
            // Set row layout parameters
            TableLayout.LayoutParams rowParams = new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            );
            row.setLayoutParams(rowParams);
            
            // Create and add cells for this row
            TextView serialNumberCell = createTableCell((i + 1) + "");
            TextView dateCell = createTableCell(item.getDate());
            TextView officeCell = createTableCell(item.getOfficeName());
            TextView checkInCell = createTableCell(item.getCheckInTime());
            TextView checkOutCell = createTableCell(item.getCheckOutTime());
            
            // Set background color for each cell based on row position (alternating)
            int bgColor = i % 2 == 0 ? colorEven : colorOdd;
            serialNumberCell.setBackgroundColor(bgColor);
            dateCell.setBackgroundColor(bgColor);
            officeCell.setBackgroundColor(bgColor);
            checkInCell.setBackgroundColor(bgColor);
            checkOutCell.setBackgroundColor(bgColor);
            
            // Set fixed widths for columns
            serialNumberCell.setMinWidth(80);
            dateCell.setMinWidth(120);
            officeCell.setMinWidth(180);
            checkInCell.setMinWidth(100);
            checkOutCell.setMinWidth(100);
            
            // Add cells to the row
            row.addView(serialNumberCell);
            row.addView(dateCell);
            row.addView(officeCell);
            row.addView(checkInCell);
            row.addView(checkOutCell);
            
            // Add the row to the table
            table.addView(row);
        }
    }
    
    // Helper method to create a table cell TextView
    private TextView createTableCell(String text) {
        TextView cell = new TextView(requireContext());
        cell.setText(text != null ? text : "");
        cell.setPadding(12, 12, 12, 12);
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
    
    // Alternative query method that doesn't require a composite index
    private void tryAlternativeQuery(String sevarthId, String startDate, String endDate) {
        if (binding == null) return;
        
        // Just filter by sevarthId and then filter the results in code
        db.collection("face-recognition-attendance")
            .whereEqualTo("sevarthId", sevarthId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (binding == null) return;
                
                try {
                    List<AttendanceRecord> filteredRecords = new ArrayList<>();
                    
                    // First, filter records within the date range
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            // Get the date string from the document
                            String recordDate = document.getString("date");
                            
                            // Skip records outside our date range
                            if (recordDate == null || 
                                recordDate.compareTo(startDate) < 0 || 
                                recordDate.compareTo(endDate) > 0) {
                                continue;
                            }
                            
                            // Create attendance record from document
                            AttendanceRecord record = new AttendanceRecord();
                            record.setId(document.getId());
                            record.setDate(recordDate);
                            record.setStatus(document.getString("status"));
                            record.setTime(document.getString("time"));
                            record.setTimestamp(document.getTimestamp("timestamp"));
                            record.setType(document.getString("type"));
                            record.setUserId(document.getString("userId"));
                            record.setUserName(document.getString("userName"));
                            record.setSevarthId(document.getString("sevarthId"));
                            
                            // Get office name
                            String officeName = document.getString("officeName");
                            if (officeName == null || officeName.isEmpty()) {
                                officeName = "Unknown Office";
                            }
                            record.setOfficeName(officeName);
                            
                            // Verification confidence
                            if (document.contains("verificationConfidence")) {
                                Double confidence = document.getDouble("verificationConfidence");
                                if (confidence != null) {
                                    record.setVerificationConfidence(confidence);
                                }
                            }
                            
                            filteredRecords.add(record);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing attendance record in alternative query", e);
                        }
                    }
                    
                    // Group records by date and office to create the report items
                    // Use a map where the key is a composite of date and office name
                    Map<String, AttendanceReportItem> reportItemMap = new HashMap<>();
                    
                    for (AttendanceRecord record : filteredRecords) {
                        String key = record.getDate() + "_" + record.getOfficeName();
                        AttendanceReportItem reportItem = reportItemMap.get(key);
                        
                        if (reportItem == null) {
                            reportItem = new AttendanceReportItem(record.getDate(), record.getOfficeName());
                            reportItemMap.put(key, reportItem);
                        }
                        
                        // Set check-in or check-out time based on record type
                        if ("check_in".equals(record.getType())) {
                            reportItem.setCheckInTime(record.getTime());
                        } else if ("check_out".equals(record.getType())) {
                            reportItem.setCheckOutTime(record.getTime());
                        }
                    }
                    
                    // Convert map values to a list and sort by date
                    List<AttendanceReportItem> reportItems = new ArrayList<>(reportItemMap.values());
                    reportItems.sort((a, b) -> a.getDate().compareTo(b.getDate()));
                    
                    // Update UI
                    if (reportItems.isEmpty()) {
                        binding.noRecordsText.setVisibility(View.VISIBLE);
                        binding.attendanceTable.setVisibility(View.GONE);
                    } else {
                        binding.noRecordsText.setVisibility(View.GONE);
                        binding.attendanceTable.setVisibility(View.VISIBLE);
                        
                        // Clear the table except for the header row
                        clearTableRows();
                        
                        // Populate table with the data
                        populateTable(reportItems);
                    }
                    
                    // Show results
                    binding.resultsContainer.setVisibility(View.VISIBLE);
                    binding.progressBar.setVisibility(View.GONE);
                    
                    // Show a toast that we're using client-side filtering
                    Toast.makeText(requireContext(), 
                        "Showing attendance report with client-side processing.", 
                        Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error processing attendance records", e);
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Error generating report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                if (binding == null) return;
                
                Log.e(TAG, "Error with alternative query", e);
                Toast.makeText(requireContext(), "Failed to generate report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                binding.progressBar.setVisibility(View.GONE);
            });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 