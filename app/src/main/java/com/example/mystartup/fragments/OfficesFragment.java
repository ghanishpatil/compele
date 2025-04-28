package com.example.mystartup.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mystartup.AddOfficeLocationActivity;
import com.example.mystartup.R;
import com.example.mystartup.adapters.OfficeLocationAdapter;
import com.example.mystartup.models.OfficeLocation;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class OfficesFragment extends Fragment implements OfficeLocationAdapter.OnOfficeLocationClickListener {

    private static final int ADD_LOCATION_REQUEST = 1001;
    private static final int EDIT_LOCATION_REQUEST = 1002;
    
    private RecyclerView recyclerView;
    private OfficeLocationAdapter adapter;
    private List<OfficeLocation> locationList;
    private View emptyView;
    private FirebaseFirestore db;
    private FloatingActionButton addButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_offices, container, false);
        
        recyclerView = view.findViewById(R.id.officesRecyclerView);
        emptyView = view.findViewById(R.id.emptyView);
        
        db = FirebaseFirestore.getInstance();
        locationList = new ArrayList<>();
        adapter = new OfficeLocationAdapter(locationList, this);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        // Setup add button from the MainActivity
        addButton = requireActivity().findViewById(R.id.addButton);
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AddOfficeLocationActivity.class);
            startActivityForResult(intent, ADD_LOCATION_REQUEST);
        });
        
        loadOfficeLocations();
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        addButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Hide the FAB when this fragment is not visible
        if (addButton != null) {
            addButton.setVisibility(View.GONE);
        }
    }

    private void loadOfficeLocations() {
        db.collection("office_locations")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                locationList.clear();
                
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    OfficeLocation location = document.toObject(OfficeLocation.class);
                    locationList.add(location);
                }
                
                adapter.notifyDataSetChanged();
                updateViewVisibility();
            })
            .addOnFailureListener(e -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Failed to load office locations", Toast.LENGTH_SHORT).show();
                }
                updateViewVisibility();
            });
    }
    
    private void updateViewVisibility() {
        if (locationList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == ADD_LOCATION_REQUEST || requestCode == EDIT_LOCATION_REQUEST) && 
            resultCode == getActivity().RESULT_OK) {
            loadOfficeLocations();
        }
    }

    @Override
    public void onEditClick(OfficeLocation location) {
        Intent intent = new Intent(getActivity(), AddOfficeLocationActivity.class);
        intent.putExtra("LOCATION_ID", location.getId());
        startActivityForResult(intent, EDIT_LOCATION_REQUEST);
    }

    @Override
    public void onDeleteClick(OfficeLocation location) {
        new AlertDialog.Builder(getContext())
            .setTitle("Delete Office Location")
            .setMessage("Are you sure you want to delete this office location?")
            .setPositiveButton("Delete", (dialog, which) -> {
                deleteOfficeLocation(location.getId());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void deleteOfficeLocation(String locationId) {
        db.collection("office_locations").document(locationId)
            .delete()
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(getContext(), "Location deleted successfully", Toast.LENGTH_SHORT).show();
                loadOfficeLocations();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(getContext(), "Failed to delete location", Toast.LENGTH_SHORT).show();
            });
    }
} 