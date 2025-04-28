package com.example.mystartup.utils;

import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Utility class for handling common Firebase operations
 */
public class FirebaseUtils {
    private static final String TAG = "FirebaseUtils";
    
    /**
     * Reauthenticate the current admin user to refresh their token
     * This helps solve permission issues when performing admin tasks
     * 
     * @param email Admin email
     * @param password Admin password
     * @param successCallback Callback to run on success
     * @param failureCallback Callback to run on failure
     */
    public static void reauthenticateAdmin(String email, String password, 
                                          Runnable successCallback, 
                                          Runnable failureCallback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        
        if (currentUser != null) {
            // Create credential
            AuthCredential credential = EmailAuthProvider.getCredential(email, password);
            
            // Reauthenticate without signing out
            currentUser.reauthenticate(credential)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Get fresh token
                            currentUser.getIdToken(true)
                                    .addOnCompleteListener(tokenTask -> {
                                        if (tokenTask.isSuccessful()) {
                                            Log.d(TAG, "Token refreshed successfully");
                                            // Verify that the user is an admin
                                            verifyAdminStatus(currentUser.getUid(), isAdmin -> {
                                                if (isAdmin) {
                                                    if (successCallback != null) {
                                                        successCallback.run();
                                                    }
                                                } else {
                                                    Log.e(TAG, "User is not an admin");
                                                    if (failureCallback != null) {
                                                        failureCallback.run();
                                                    }
                                                }
                                            });
                                        } else {
                                            Log.e(TAG, "Failed to refresh token", tokenTask.getException());
                                            if (failureCallback != null) {
                                                failureCallback.run();
                                            }
                                        }
                                    });
                        } else {
                            Log.e(TAG, "Reauthentication failed", task.getException());
                            if (failureCallback != null) {
                                failureCallback.run();
                            }
                        }
                    });
        } else {
            // Sign in if no current user
            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                // Get fresh token
                                user.getIdToken(true)
                                        .addOnCompleteListener(tokenTask -> {
                                            if (tokenTask.isSuccessful()) {
                                                Log.d(TAG, "Token refreshed successfully");
                                                // Verify admin status
                                                verifyAdminStatus(user.getUid(), isAdmin -> {
                                                    if (isAdmin) {
                                                        if (successCallback != null) {
                                                            successCallback.run();
                                                        }
                                                    } else {
                                                        Log.e(TAG, "User is not an admin");
                                                        if (failureCallback != null) {
                                                            failureCallback.run();
                                                        }
                                                    }
                                                });
                                            } else {
                                                Log.e(TAG, "Failed to refresh token", tokenTask.getException());
                                                if (failureCallback != null) {
                                                    failureCallback.run();
                                                }
                                            }
                                        });
                            }
                        } else {
                            Log.e(TAG, "Authentication failed", task.getException());
                            if (failureCallback != null) {
                                failureCallback.run();
                            }
                        }
                    });
        }
    }
    
    /**
     * Verify if a user has admin status by checking Firestore
     * 
     * @param uid User ID to check
     * @param callback Callback with boolean result
     */
    public static void verifyAdminStatus(String uid, AdminStatusCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("admins").document(uid).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        boolean isAdmin = document != null && document.exists();
                        Log.d(TAG, "Admin status for " + uid + ": " + isAdmin);
                        callback.onResult(isAdmin);
                    } else {
                        Log.e(TAG, "Error checking admin status", task.getException());
                        callback.onResult(false);
                    }
                });
    }
    
    /**
     * Check if the current user has admin permissions
     */
    public static void isCurrentUserAdmin(AdminStatusCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            verifyAdminStatus(user.getUid(), callback);
        } else {
            callback.onResult(false);
        }
    }
    
    /**
     * Callback interface for admin status verification
     */
    public interface AdminStatusCallback {
        void onResult(boolean isAdmin);
    }
} 