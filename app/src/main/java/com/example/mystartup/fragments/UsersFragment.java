package com.example.mystartup.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mystartup.AddUserActivity;
import com.example.mystartup.R;
import com.example.mystartup.adapters.UserAdapter;
import com.example.mystartup.api.ApiService;
import com.example.mystartup.api.RetrofitClient;
import com.example.mystartup.models.User;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UsersFragment extends Fragment implements UserAdapter.OnUserClickListener {

    private static final String TAG = "UsersFragment";
    private static final int ADD_USER_REQUEST = 2001;
    private static final int EDIT_USER_REQUEST = 2002;
    
    private RecyclerView recyclerView;
    private UserAdapter adapter;
    private List<User> userList;
    private View emptyView;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private ApiService apiService;
    private FloatingActionButton addButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_users, container, false);
        
        recyclerView = view.findViewById(R.id.usersRecyclerView);
        emptyView = view.findViewById(R.id.emptyView);
        
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        apiService = RetrofitClient.getInstance().getApiService();
        
        userList = new ArrayList<>();
        adapter = new UserAdapter(userList, this);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        // Setup add button from the MainActivity
        addButton = requireActivity().findViewById(R.id.addButton);
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AddUserActivity.class);
            startActivityForResult(intent, ADD_USER_REQUEST);
        });
        
        loadUsers();
        
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

    private void loadUsers() {
        db.collection("users")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                userList.clear();
                
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        User user = document.toObject(User.class);
                        // Handle legacy data format
                        if (user.getLocationIds() == null) {
                            user.setLocationIds(new ArrayList<>());
                        }
                        if (user.getLocationNames() == null) {
                            user.setLocationNames(new ArrayList<>());
                        }
                        userList.add(user);
                    } catch (Exception e) {
                        Log.e(TAG, "Error converting document to User: " + document.getId(), e);
                    }
                }
                
                adapter.notifyDataSetChanged();
                updateViewVisibility();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading users", e);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Failed to load users", Toast.LENGTH_SHORT).show();
                }
                updateViewVisibility();
            });
    }
    
    private void updateViewVisibility() {
        if (userList.isEmpty()) {
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
        if ((requestCode == ADD_USER_REQUEST || requestCode == EDIT_USER_REQUEST) && 
            resultCode == getActivity().RESULT_OK) {
            loadUsers();
        }
    }

    @Override
    public void onEditClick(User user) {
        Intent intent = new Intent(getActivity(), AddUserActivity.class);
        intent.putExtra("USER_ID", user.getSevarthId());
        startActivityForResult(intent, EDIT_USER_REQUEST);
    }

    @Override
    public void onDeleteClick(User user) {
        new AlertDialog.Builder(getContext())
            .setTitle("Delete User")
            .setMessage("Are you sure you want to delete this user?")
            .setPositiveButton("Delete", (dialog, which) -> {
                deleteUser(user);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void deleteUser(User user) {
        if (getContext() == null) return;

        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(getContext())
            .setMessage("Deleting user...")
            .setCancelable(false)
            .create();
        loadingDialog.show();

        // Get the current user's token
        mAuth.getCurrentUser().getIdToken(true)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String token = task.getResult().getToken();
                    
                    // First delete from Firestore
                    db.collection("users")
                        .whereEqualTo("sevarthId", user.getSevarthId())
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            // Delete all matching documents (in case of duplicates)
                            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                document.getReference().delete();
                            }
                            
                            // Then call backend API to clean up Firebase Auth and Storage
                            apiService.deleteUser("Bearer " + token, user.getSevarthId())
                                .enqueue(new Callback<Void>() {
                                    @Override
                                    public void onResponse(Call<Void> call, Response<Void> response) {
                                        loadingDialog.dismiss();
                                        if (response.isSuccessful()) {
                                            Toast.makeText(getContext(), "User deleted successfully", Toast.LENGTH_SHORT).show();
                                            loadUsers(); // Refresh the list
                                        } else {
                                            String errorMessage = "Failed to delete user";
                                            try {
                                                if (response.errorBody() != null) {
                                                    errorMessage = response.errorBody().string();
                                                }
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error reading error response", e);
                                            }
                                            Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
                                            loadUsers(); // Refresh anyway to show current state
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<Void> call, Throwable t) {
                                        loadingDialog.dismiss();
                                        Log.e(TAG, "Error deleting user", t);
                                        Toast.makeText(getContext(), 
                                            "Network error: " + t.getMessage(), 
                                            Toast.LENGTH_SHORT).show();
                                        loadUsers(); // Refresh anyway to show current state
                                    }
                                });
                        })
                        .addOnFailureListener(e -> {
                            loadingDialog.dismiss();
                            Log.e(TAG, "Error querying user documents", e);
                            Toast.makeText(getContext(), 
                                "Failed to delete user: " + e.getMessage(), 
                                Toast.LENGTH_SHORT).show();
                        });
                } else {
                    loadingDialog.dismiss();
                    Toast.makeText(getContext(), 
                        "Authentication error: " + task.getException().getMessage(), 
                        Toast.LENGTH_SHORT).show();
                }
            });
    }
} 