package com.example.mystartup.fragments;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mystartup.databinding.FragmentLocationAttendanceReportBinding;
import com.example.mystartup.models.OfficeLocation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class LocationAttendanceReportFragment extends Fragment {
    private FragmentLocationAttendanceReportBinding binding;
    private Calendar calendar;
    private SimpleDateFormat dateFormatter;
    private List<OfficeLocation> locationList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calendar = Calendar.getInstance();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        locationList = new ArrayList<>(); // This will be populated from API
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
        setupLocationDropdown();
        setupDatePickers();
        setupGenerateReportButton();
    }

    private void setupLocationDropdown() {
        // TODO: Fetch locations from API and populate the dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            new String[]{"Location 1", "Location 2", "Location 3"} // Placeholder data
        );
        binding.locationDropdown.setAdapter(adapter);
    }

    private void setupDatePickers() {
        binding.startDateInput.setOnClickListener(v -> showDatePicker(true));
        binding.endDateInput.setOnClickListener(v -> showDatePicker(false));
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

            // TODO: Generate report using the selected parameters
            generateReport(selectedLocation, startDate, endDate);
        });
    }

    private void generateReport(String location, String startDate, String endDate) {
        // TODO: Implement report generation logic
        // This will involve making API calls to fetch attendance data
        // and displaying it in a suitable format
        Toast.makeText(requireContext(), 
            "Generating report for " + location + " from " + startDate + " to " + endDate, 
            Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 