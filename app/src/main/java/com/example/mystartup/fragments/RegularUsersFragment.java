package com.example.mystartup.fragments;

import android.content.Context;
import android.content.SharedPreferences;
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

import com.example.mystartup.R;
import com.example.mystartup.adapters.UserAdapter;
import com.example.mystartup.api.ApiService;
import com.example.mystartup.api.RetrofitClient;
import com.example.mystartup.api.UsersResponse;
import com.example.mystartup.models.User;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RegularUsersFragment extends Fragment implements UserAdapter.OnUserClickListener {

    private RecyclerView recyclerView;
    private UserAdapter adapter;
    private List<User> userList;
    private View emptyView;
    private FirebaseFirestore db;
    private ApiService apiService;
    private static final String PREF_NAME = "AuthPrefs";
    private static final String KEY_AUTH_TOKEN = "auth_token";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_regular_users, container, false);
        
        recyclerView = view.findViewById(R.id.usersRecyclerView);
        emptyView = view.findViewById(R.id.emptyView);
        
        db = FirebaseFirestore.getInstance();
        userList = new ArrayList<>();
        adapter = new UserAdapter(userList, this);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        setupRetrofit();
        
        // Try API first, then fall back to Firestore if API fails
        loadUsersFromApi();
        
        return view;
    }
    
    private void setupRetrofit() {
        apiService = RetrofitClient.getInstance().getApiService();
    }
    
    private void loadUsersFromApi() {
        if (getContext() == null) return;
        
        SharedPreferences prefs = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_AUTH_TOKEN, "");
        
        if (token.isEmpty()) {
            // Fallback to Firestore if no token
            loadUsersFromFirestore();
            return;
        }
        
        apiService.getUsers("Bearer " + token).enqueue(new Callback<UsersResponse>() {
            @Override
            public void onResponse(Call<UsersResponse> call, Response<UsersResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    userList.clear();
                    userList.addAll(response.body().getUsers());
                    adapter.notifyDataSetChanged();
                    
                    updateViewVisibility();
                } else {
                    // Fallback to Firestore
                    loadUsersFromFirestore();
                }
            }

            @Override
            public void onFailure(Call<UsersResponse> call, Throwable t) {
                // Fallback to Firestore
                loadUsersFromFirestore();
            }
        });
    }
    
    private void loadUsersFromFirestore() {
        db.collection("users")
            .whereEqualTo("role", "user")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                userList.clear();
                
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    User user = document.toObject(User.class);
                    userList.add(user);
                }
                
                adapter.notifyDataSetChanged();
                updateViewVisibility();
            })
            .addOnFailureListener(e -> {
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
    public void onEditClick(User user) {
        // Implement edit functionality if needed for regular users
        Toast.makeText(getContext(), "Edit not available for regular users", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onDeleteClick(User user) {
        // Implement delete functionality if needed for regular users
        Toast.makeText(getContext(), "Delete not available for regular users", Toast.LENGTH_SHORT).show();
    }
} 