package com.example.mystartup.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mystartup.R;
import com.example.mystartup.models.User;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
    
    private List<User> userList;
    private OnUserClickListener listener;
    
    public interface OnUserClickListener {
        void onEditClick(User user);
        void onDeleteClick(User user);
    }
    
    public UserAdapter(List<User> userList, OnUserClickListener listener) {
        this.userList = userList;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);
        
        holder.userNameTextView.setText(user.getFullName());
        holder.sevarthIdTextView.setText("Sevarth ID: " + user.getSevarthId());
        holder.emailTextView.setText("Email: " + user.getEmail());
        
        // Handle multiple locations by joining them with commas
        List<String> locationNames = user.getLocationNames();
        String locationText = "Location: " + (locationNames != null && !locationNames.isEmpty() ? 
            TextUtils.join(", ", locationNames) : "Not assigned");
        holder.locationTextView.setText(locationText);

        holder.editButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(user);
            }
        });
        
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(user);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return userList.size();
    }
    
    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView userNameTextView;
        TextView sevarthIdTextView;
        TextView emailTextView;
        TextView locationTextView;
        ImageButton editButton;
        ImageButton deleteButton;
        
        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            userNameTextView = itemView.findViewById(R.id.userNameTextView);
            sevarthIdTextView = itemView.findViewById(R.id.sevarthIdTextView);
            emailTextView = itemView.findViewById(R.id.emailTextView);
            locationTextView = itemView.findViewById(R.id.locationTextView);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
} 