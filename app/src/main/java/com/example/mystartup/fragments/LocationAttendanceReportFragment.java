package com.example.mystartup.fragments;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.mystartup.R;
import com.example.mystartup.databinding.FragmentLocationAttendanceReportBinding;
import com.example.mystartup.models.AttendanceRecord;
import com.example.mystartup.models.OfficeLocation;
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

public class LocationAttendanceReportFragment extends Fragment {
    private static final String TAG = "LocationAttendanceReport";
    private FragmentLocationAttendanceReportBinding binding;
    private Calendar calendar;
    private SimpleDateFormat dateFormatter;
    private SimpleDateFormat apiDateFormatter;
    private List<OfficeLocation> locationList;
    private Map<String, String> locationDisplayToId;
    private FirebaseFirestore db;
    private int colorEven;
    private int colorOdd;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calendar = Calendar.getInstance();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        apiDateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        locationList = new ArrayList<>();
        locationDisplayToId = new HashMap<>();
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLocationAttendanceReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            // Initialize row colors for alternating table rows
            colorEven = ContextCompat.getColor(requireContext(), R.color.tableRowEven);
            colorOdd = ContextCompat.getColor(requireContext(), R.color.tableRowOdd);
            
            fetchLocations();
        setupDatePickers();
        setupGenerateReportButton();
            setupReportTable();
            
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
            headerRow.addView(createHeaderCell("Date"));
            headerRow.addView(createHeaderCell("Sevarth ID"));
            headerRow.addView(createHeaderCell("Name"));
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

    private void fetchLocations() {
        try {
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(View.VISIBLE);
            }
            
            Log.d(TAG, "Fetching office locations from Firestore");
            
            // Use a simpler query without the isActive filter to see if we get any results
            db.collection("office_locations")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Locations query successful. Got " + queryDocumentSnapshots.size() + " documents");
                    
                    locationList.clear();
                    locationDisplayToId.clear();
                    List<String> locationNames = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Log.d(TAG, "Processing location document: " + document.getId());
                            
                            // Try both OfficeLocation object mapping and manual field extraction
                            OfficeLocation location = document.toObject(OfficeLocation.class);
                            
                            // Fallback to manual field extraction if the name is null
                            String locationName = (location != null && location.getName() != null) 
                                                ? location.getName() 
                                                : document.getString("name");
                            
                            if (locationName != null && !locationName.isEmpty()) {
                                Log.d(TAG, "Found location: " + locationName);
                                if (location != null) {
                                    location.setId(document.getId());
                                    locationList.add(location);
                                }
                                
                                locationNames.add(locationName);
                                locationDisplayToId.put(locationName, document.getId());
                            } else {
                                Log.w(TAG, "Location has no name: " + document.getId());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing location document: " + document.getId(), e);
                        }
                    }
                    
                    // Check if we found any locations
                    if (locationNames.isEmpty()) {
                        Log.w(TAG, "No locations found in the database");
                        
                        // Add some placeholder data for testing
                        locationNames.add("Main Office");
                        locationNames.add("Branch Office");
                        locationNames.add("Regional Office");
                        locationNames.add("Field Office");
                        locationNames.add("Headquarters");
                        
                        for (String name : locationNames) {
                            locationDisplayToId.put(name, name);
                        }
                    }
                    
                    setupLocationDropdown(locationNames);
                    
                    if (binding.progressBar != null) {
                        binding.progressBar.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching locations", e);
                    Toast.makeText(requireContext(), "Failed to load locations: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    
                    if (binding.progressBar != null) {
                        binding.progressBar.setVisibility(View.GONE);
                    }
                    
                    // Add some placeholder data for testing
                    List<String> placeholderLocations = new ArrayList<>();
                    placeholderLocations.add("Main Office");
                    placeholderLocations.add("Branch Office");
                    placeholderLocations.add("Regional Office");
                    placeholderLocations.add("Field Office");
                    placeholderLocations.add("Headquarters");
                    
                    for (String name : placeholderLocations) {
                        locationDisplayToId.put(name, name);
                    }
                    
                    setupLocationDropdown(placeholderLocations);
                });
        } catch (Exception e) {
            Log.e(TAG, "Error in fetchLocations", e);
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(View.GONE);
            }
            
            // Fallback to hardcoded locations if there's an exception
            List<String> fallbackLocations = new ArrayList<>();
            fallbackLocations.add("Main Office");
            fallbackLocations.add("Branch Office");
            fallbackLocations.add("Regional Office");
            
            for (String name : fallbackLocations) {
                locationDisplayToId.put(name, name);
            }
            
            setupLocationDropdown(fallbackLocations);
        }
    }

    private void setupLocationDropdown(List<String> locationNames) {
        try {
            if (locationNames.isEmpty()) {
                Log.w(TAG, "No location names available for dropdown");
                locationNames.add("No locations found");
            } else {
                Log.d(TAG, "Setting up location dropdown with " + locationNames.size() + " locations:");
                for (String name : locationNames) {
                    Log.d(TAG, "  - Location: '" + name + "'");
                }
            }
            
            // Create a simple adapter for the dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
                locationNames
        );
            
            if (binding.locationDropdown != null) {
        binding.locationDropdown.setAdapter(adapter);
                
                // Set a default selection if there are locations available
                if (locationNames.size() > 0 && !locationNames.get(0).equals("No locations found")) {
                    binding.locationDropdown.setText(locationNames.get(0), false);
                }
                
                // Add a listener to log the selected location
                binding.locationDropdown.setOnItemClickListener((parent, view, position, id) -> {
                    String selectedLocation = locationNames.get(position);
                    Log.d(TAG, "Selected location: '" + selectedLocation + "'");
                });
            } else {
                Log.e(TAG, "Location dropdown is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up location dropdown", e);
        }
    }

    private void setupDatePickers() {
        try {
            if (binding.startDateInput != null) {
        binding.startDateInput.setOnClickListener(v -> showDatePicker(true));
            }
            
            if (binding.endDateInput != null) {
        binding.endDateInput.setOnClickListener(v -> showDatePicker(false));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up date pickers", e);
        }
    }

    private void showDatePicker(boolean isStartDate) {
        DatePickerDialog dialog = new DatePickerDialog(
            requireContext(),
            (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                String formattedDate = dateFormatter.format(calendar.getTime());
                if (isStartDate) {
                    binding.startDateInput.setText(formattedDate);
                } else {
                    binding.endDateInput.setText(formattedDate);
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void setupGenerateReportButton() {
        try {
            if (binding.generateReportButton != null) {
        binding.generateReportButton.setOnClickListener(v -> {
            String selectedLocation = binding.locationDropdown.getText().toString();
            String startDate = binding.startDateInput.getText().toString();
            String endDate = binding.endDateInput.getText().toString();

            if (selectedLocation.isEmpty()) {
                Toast.makeText(requireContext(), "Please select a location", Toast.LENGTH_SHORT).show();
                return;
            }

            if (startDate.isEmpty() || endDate.isEmpty()) {
                Toast.makeText(requireContext(), "Please select both start and end dates", Toast.LENGTH_SHORT).show();
                return;
            }

                    // Convert dates to API format (yyyy-MM-dd)
                    String apiStartDate = "";
                    String apiEndDate = "";
                    try {
                        Date start = dateFormatter.parse(startDate);
                        Date end = dateFormatter.parse(endDate);
                        
                        if (start != null && end != null) {
                            if (start.after(end)) {
                                Toast.makeText(requireContext(), "Start date cannot be after end date", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            
                            apiStartDate = apiDateFormatter.format(start);
                            apiEndDate = apiDateFormatter.format(end);
                        }
                    } catch (ParseException e) {
                        Log.e(TAG, "Date parsing error", e);
                        Toast.makeText(requireContext(), "Invalid date format", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // Generate report
                    generateReport(selectedLocation, apiStartDate, apiEndDate);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up generate button", e);
        }
    }

    private void generateReport(String locationName, String startDate, String endDate) {
        try {
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(View.VISIBLE);
            }
            
            if (binding.resultsContainer != null) {
                binding.resultsContainer.setVisibility(View.GONE);
            }
            
            // Clear existing table rows except header
            clearTableRows();
            
            Log.d(TAG, "Generating report for location: '" + locationName + "', date range: " + startDate + " to " + endDate);
            
            // Query Firestore for attendance records by location and date range
            db.collection("face-recognition-attendance")
                .whereEqualTo("officeName", locationName)
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Query successful. Got " + queryDocumentSnapshots.size() + " attendance records");
                    
                    if (queryDocumentSnapshots.size() > 0) {
                        // Process the records we found
                        processAttendanceRecords(queryDocumentSnapshots, locationName);
                    } else {
                        // No records found with officeName field, try alternative approaches
                        Log.d(TAG, "No records found with officeName field, trying alternatives");
                        tryAlternativeQuery(locationName, startDate, endDate);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching attendance records: " + e.getMessage(), e);
                    
                    // Don't show error toast here because we're going to try alternative approaches
                    Log.d(TAG, "Primary query failed, trying alternatives");
                    
                    if (binding.progressBar != null) {
                        binding.progressBar.setVisibility(View.GONE);
                    }
                    
                    // Try alternative query
                    tryAlternativeQuery(locationName, startDate, endDate);
                });
        } catch (Exception e) {
            Log.e(TAG, "Error generating report", e);
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(View.GONE);
            }
            Toast.makeText(requireContext(), "Error preparing report query: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void tryAlternativeQuery(String locationName, String startDate, String endDate) {
        try {
            Log.d(TAG, "Trying alternative query approach for location: '" + locationName + "'");
            
            // First, try using a different field name for the office name (locationName instead of officeName)
            db.collection("face-recognition-attendance")
                .whereEqualTo("locationName", locationName)
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Alternative query with locationName successful. Got " + queryDocumentSnapshots.size() + " records");
                    
                    if (queryDocumentSnapshots.size() > 0) {
                        processAttendanceRecords(queryDocumentSnapshots, locationName);
                    } else {
                        // Try another field name (office_name)
                        trySecondAlternativeQuery(locationName, startDate, endDate);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error with locationName alternative query: " + e.getMessage(), e);
                    // Try another field name
                    trySecondAlternativeQuery(locationName, startDate, endDate);
                });
        } catch (Exception e) {
            Log.e(TAG, "Error in alternative query", e);
            tryClientSideFiltering(locationName, startDate, endDate);
        }
    }
    
    private void trySecondAlternativeQuery(String locationName, String startDate, String endDate) {
        try {
            Log.d(TAG, "Trying second alternative query approach with office_name for location: '" + locationName + "'");
            
            // Try using office_name field
            db.collection("face-recognition-attendance")
                .whereEqualTo("office_name", locationName)
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Alternative query with office_name successful. Got " + queryDocumentSnapshots.size() + " records");
                    
                    if (queryDocumentSnapshots.size() > 0) {
                        processAttendanceRecords(queryDocumentSnapshots, locationName);
                    } else {
                        // Fall back to client-side filtering
                        tryClientSideFiltering(locationName, startDate, endDate);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error with office_name alternative query: " + e.getMessage(), e);
                    // Fall back to client-side filtering
                    tryClientSideFiltering(locationName, startDate, endDate);
                });
        } catch (Exception e) {
            Log.e(TAG, "Error in second alternative query", e);
            tryClientSideFiltering(locationName, startDate, endDate);
        }
    }
    
    private void tryClientSideFiltering(String locationName, String startDate, String endDate) {
        try {
            Log.d(TAG, "Trying client-side filtering for location: '" + locationName + "'");
            
            // Query just by date range and filter by location in the app
            db.collection("face-recognition-attendance")
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Client-side filtering query got " + queryDocumentSnapshots.size() + " records");
                    
                    try {
                        // Process attendance records
                        Map<String, Map<String, AttendanceRecord>> userDailyRecords = new HashMap<>();
                        
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            try {
                                String date = document.getString("date");
                                String userId = document.getString("userId");
                                String type = document.getString("type");
                                
                                if (date == null || userId == null || type == null) {
                                    continue;
                                }
                                
                                // Check if this record is for the requested location
                                String recordLocation = document.getString("officeName");
                                if (recordLocation == null) {
                                    recordLocation = document.getString("locationName");
                                }
                                if (recordLocation == null) {
                                    recordLocation = document.getString("office_name");
                                }
                                
                                Log.d(TAG, "Record location: '" + recordLocation + "', comparing with: '" + locationName + "'");
                                
                                // Skip if not matching the requested location
                                if (recordLocation == null || !recordLocation.equals(locationName)) {
                                    continue;
                                }
                                
                                // Create a key for each user's daily records
                                String key = date + "_" + userId;
                                
                                // Get or create the user's daily record map
                                Map<String, AttendanceRecord> dailyRecord = userDailyRecords.get(key);
                                if (dailyRecord == null) {
                                    dailyRecord = new HashMap<>();
                                    userDailyRecords.put(key, dailyRecord);
                                }
                                
                                // Create and store the record
                                AttendanceRecord record = new AttendanceRecord();
                                record.setId(document.getId());
                                record.setDate(date);
                                record.setUserId(userId);
                                record.setUserName(document.getString("userName"));
                                record.setSevarthId(document.getString("sevarthId"));
                                record.setOfficeName(recordLocation);
                                record.setTime(document.getString("time"));
                                record.setType(type);
                                
                                // Store by type (check_in or check_out)
                                dailyRecord.put(type, record);
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing record", e);
                            }
                        }
                        
                        // Now create table rows for each user's daily records
                        List<TableRowData> rowDataList = new ArrayList<>();
                        
                        for (Map.Entry<String, Map<String, AttendanceRecord>> entry : userDailyRecords.entrySet()) {
                            Map<String, AttendanceRecord> dailyRecord = entry.getValue();
                            
                            // Get check-in and check-out records
                            AttendanceRecord checkIn = dailyRecord.get("check_in");
                            AttendanceRecord checkOut = dailyRecord.get("check_out");
                            
                            // Use check-in for user details, fall back to check-out if needed
                            AttendanceRecord primaryRecord = checkIn != null ? checkIn : checkOut;
                            
                            if (primaryRecord != null) {
                                TableRowData rowData = new TableRowData();
                                rowData.date = primaryRecord.getDate();
                                rowData.sevarthId = primaryRecord.getSevarthId();
                                rowData.userName = primaryRecord.getUserName();
                                rowData.checkInTime = checkIn != null ? checkIn.getTime() : "-";
                                rowData.checkOutTime = checkOut != null ? checkOut.getTime() : "-";
                                
                                rowDataList.add(rowData);
                            }
                        }
                        
                        Log.d(TAG, "Processed " + rowDataList.size() + " attendance records for the table");
                        
                        // Sort by date and then by name
                        rowDataList.sort((a, b) -> {
                            int dateComparison = a.date.compareTo(b.date);
                            if (dateComparison != 0) {
                                return dateComparison;
                            }
                            return a.userName.compareTo(b.userName);
                        });
                        
                        // Add rows to the table
                        for (int i = 0; i < rowDataList.size(); i++) {
                            addTableRow(i + 1, rowDataList.get(i), i % 2 == 0);
                        }
                        
                        // Show results or "no records" message
                        if (rowDataList.isEmpty()) {
                            Log.w(TAG, "No attendance records found for location: " + locationName + " after client-side filtering");
                            
                            if (binding.noRecordsText != null) {
                                binding.noRecordsText.setVisibility(View.VISIBLE);
                            }
                            if (binding.attendanceTable != null) {
                                binding.attendanceTable.setVisibility(View.GONE);
                            }
                            
                            // Show message that no records were found
                            Toast.makeText(requireContext(), 
                                "No attendance records found for " + locationName, 
                                Toast.LENGTH_SHORT).show();
                        } else {
                            if (binding.noRecordsText != null) {
                                binding.noRecordsText.setVisibility(View.GONE);
                            }
                            if (binding.attendanceTable != null) {
                                binding.attendanceTable.setVisibility(View.VISIBLE);
                            }
                            
                            // Show a toast that we found records using client-side processing
        Toast.makeText(requireContext(), 
                                "Showing " + rowDataList.size() + " attendance records for " + locationName, 
            Toast.LENGTH_SHORT).show();
                        }
                        
                        // Show results container
                        if (binding.resultsContainer != null) {
                            binding.resultsContainer.setVisibility(View.VISIBLE);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing attendance records", e);
                        Toast.makeText(requireContext(), "Error processing attendance data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    } finally {
                        if (binding.progressBar != null) {
                            binding.progressBar.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error with client-side filtering query: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), "Could not retrieve attendance data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    if (binding.progressBar != null) {
                        binding.progressBar.setVisibility(View.GONE);
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error in client-side filtering query", e);
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(View.GONE);
            }
            Toast.makeText(requireContext(), "Error preparing attendance query: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void processAttendanceRecords(com.google.firebase.firestore.QuerySnapshot queryDocumentSnapshots, String locationName) {
        try {
            // Process attendance records
            Map<String, Map<String, AttendanceRecord>> userDailyRecords = new HashMap<>();
            
            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                try {
                    String date = document.getString("date");
                    String userId = document.getString("userId");
                    String type = document.getString("type");
                    
                    if (date == null || userId == null || type == null) {
                        continue;
                    }
                    
                    // Create a key for each user's daily records
                    String key = date + "_" + userId;
                    
                    // Get or create the user's daily record map
                    Map<String, AttendanceRecord> dailyRecord = userDailyRecords.get(key);
                    if (dailyRecord == null) {
                        dailyRecord = new HashMap<>();
                        userDailyRecords.put(key, dailyRecord);
                    }
                    
                    // Create and store the record
                    AttendanceRecord record = new AttendanceRecord();
                    record.setId(document.getId());
                    record.setDate(date);
                    record.setUserId(userId);
                    record.setUserName(document.getString("userName"));
                    record.setSevarthId(document.getString("sevarthId"));
                    record.setOfficeName(locationName);
                    record.setTime(document.getString("time"));
                    record.setType(type);
                    
                    // Store by type (check_in or check_out)
                    dailyRecord.put(type, record);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing record", e);
                }
            }
            
            // Now create table rows for each user's daily records
            List<TableRowData> rowDataList = new ArrayList<>();
            
            for (Map.Entry<String, Map<String, AttendanceRecord>> entry : userDailyRecords.entrySet()) {
                Map<String, AttendanceRecord> dailyRecord = entry.getValue();
                
                // Get check-in and check-out records
                AttendanceRecord checkIn = dailyRecord.get("check_in");
                AttendanceRecord checkOut = dailyRecord.get("check_out");
                
                // Use check-in for user details, fall back to check-out if needed
                AttendanceRecord primaryRecord = checkIn != null ? checkIn : checkOut;
                
                if (primaryRecord != null) {
                    TableRowData rowData = new TableRowData();
                    rowData.date = primaryRecord.getDate();
                    rowData.sevarthId = primaryRecord.getSevarthId();
                    rowData.userName = primaryRecord.getUserName();
                    rowData.checkInTime = checkIn != null ? checkIn.getTime() : "-";
                    rowData.checkOutTime = checkOut != null ? checkOut.getTime() : "-";
                    
                    rowDataList.add(rowData);
                }
            }
            
            Log.d(TAG, "Processed " + rowDataList.size() + " attendance records for the table");
            
            // Sort by date and then by name
            rowDataList.sort((a, b) -> {
                int dateComparison = a.date.compareTo(b.date);
                if (dateComparison != 0) {
                    return dateComparison;
                }
                return a.userName.compareTo(b.userName);
            });
            
            // Add rows to the table
            for (int i = 0; i < rowDataList.size(); i++) {
                addTableRow(i + 1, rowDataList.get(i), i % 2 == 0);
            }
            
            // Show results or "no records" message
            if (rowDataList.isEmpty()) {
                Log.w(TAG, "No attendance records found for location: " + locationName);
                
                if (binding.noRecordsText != null) {
                    binding.noRecordsText.setVisibility(View.VISIBLE);
                }
                if (binding.attendanceTable != null) {
                    binding.attendanceTable.setVisibility(View.GONE);
                }
                
                // Show message that no records were found
                Toast.makeText(requireContext(), 
                    "No attendance records found for " + locationName, 
                    Toast.LENGTH_SHORT).show();
            } else {
                if (binding.noRecordsText != null) {
                    binding.noRecordsText.setVisibility(View.GONE);
                }
                if (binding.attendanceTable != null) {
                    binding.attendanceTable.setVisibility(View.VISIBLE);
                }
                
                // Show a success toast with the record count
                Toast.makeText(requireContext(), 
                    "Showing " + rowDataList.size() + " attendance records for " + locationName, 
                    Toast.LENGTH_SHORT).show();
            }
            
            // Show results container
            if (binding.resultsContainer != null) {
                binding.resultsContainer.setVisibility(View.VISIBLE);
            }
            
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing attendance records", e);
            Toast.makeText(requireContext(), "Error processing attendance data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(View.GONE);
            }
        }
    }
    
    // Helper class to store table row data
    private static class TableRowData {
        String date;
        String sevarthId;
        String userName;
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
    private void addTableRow(int serialNumber, TableRowData data, boolean isEvenRow) {
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
            TextView dateCell = createTableCell(data.date, bgColor);
            TextView sevarthIdCell = createTableCell(data.sevarthId, bgColor);
            TextView nameCell = createTableCell(data.userName, bgColor);
            TextView checkInCell = createTableCell(data.checkInTime, bgColor);
            TextView checkOutCell = createTableCell(data.checkOutTime, bgColor);
            
            // Set column widths
            serialCell.setMinWidth(60);
            dateCell.setMinWidth(120);
            sevarthIdCell.setMinWidth(120);
            nameCell.setMinWidth(180);
            checkInCell.setMinWidth(100);
            checkOutCell.setMinWidth(100);
            
            // Add cells to row
            row.addView(serialCell);
            row.addView(dateCell);
            row.addView(sevarthIdCell);
            row.addView(nameCell);
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