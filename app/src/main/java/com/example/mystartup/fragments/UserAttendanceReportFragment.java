package com.example.mystartup.fragments;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mystartup.R;
import com.example.mystartup.databinding.FragmentUserAttendanceReportBinding;
import com.example.mystartup.models.User;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class UserAttendanceReportFragment extends Fragment {
    private FragmentUserAttendanceReportBinding binding;
    private Calendar calendar;
    private SimpleDateFormat dateFormatter;
    private List<User> userList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calendar = Calendar.getInstance();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        userList = new ArrayList<>(); // This will be populated from API
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentUserAttendanceReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupUserDropdown();
        setupDatePickers();
        setupGenerateReportButton();
    }

    private void setupUserDropdown() {
        // TODO: Fetch users from API and populate the dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            new String[]{"User 1", "User 2", "User 3"} // Placeholder data
        );
        binding.userDropdown.setAdapter(adapter);
    }

    private void setupDatePickers() {
        binding.startDateEdit.setOnClickListener(v -> showDatePicker(binding.startDateEdit));
        binding.endDateEdit.setOnClickListener(v -> showDatePicker(binding.endDateEdit));
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
        binding.generateReportButton.setOnClickListener(v -> {
            String selectedUser = binding.userDropdown.getText().toString();
            String startDate = binding.startDateEdit.getText().toString();
            String endDate = binding.endDateEdit.getText().toString();

            if (selectedUser.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // TODO: Generate report using the selected parameters
            generateReport(selectedUser, startDate, endDate);
        });
    }

    private void generateReport(String user, String startDate, String endDate) {
        // TODO: Implement report generation logic
        // This will involve making API calls to fetch attendance data
        // and displaying it in a suitable format
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 