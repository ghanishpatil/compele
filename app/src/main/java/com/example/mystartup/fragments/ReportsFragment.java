package com.example.mystartup.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.mystartup.R;
import com.example.mystartup.fragments.DailyAttendanceSummaryFragment;
import com.example.mystartup.fragments.UserAttendanceReportFragment;
import com.example.mystartup.fragments.LocationAttendanceReportFragment;

public class ReportsFragment extends Fragment {

    private CardView userReportsCard;
    private CardView dailyReportsCard;
    private CardView locationReportsCard;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reports, container, false);
        
        // Initialize card views
        userReportsCard = view.findViewById(R.id.userReportsCard);
        dailyReportsCard = view.findViewById(R.id.dailyReportsCard);
        locationReportsCard = view.findViewById(R.id.locationReportsCard);
        
        // Set click listeners
        userReportsCard.setOnClickListener(v -> showUserReports());
        dailyReportsCard.setOnClickListener(v -> showDailyReports());
        locationReportsCard.setOnClickListener(v -> showLocationReports());
        
        return view;
    }
    
    private void showUserReports() {
        try {
            FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
            transaction.replace(R.id.fragmentContainer, new UserAttendanceReportFragment());
            transaction.addToBackStack(null);
            transaction.commit();
        } catch (Exception e) {
            Log.e("ReportsFragment", "Error showing user reports", e);
            Toast.makeText(requireContext(), "Error showing user reports: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showDailyReports() {
        FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, new DailyAttendanceSummaryFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }
    
    private void showLocationReports() {
        FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, new LocationAttendanceReportFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }
} 