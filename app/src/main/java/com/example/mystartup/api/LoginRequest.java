package com.example.mystartup.api;

public class LoginRequest {
    private String sevarth_id;
    private String password;
    private String role;

    public LoginRequest(String sevarthId, String password, String role) {
        this.sevarth_id = sevarthId;
        this.password = password;
        this.role = role;
    }
} 