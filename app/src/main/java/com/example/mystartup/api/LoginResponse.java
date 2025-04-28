package com.example.mystartup.api;

public class LoginResponse {
    private String token;
    private UserData user;
    private String message;

    public String getToken() {
        return token;
    }

    public UserData getUser() {
        return user;
    }

    public String getMessage() {
        return message;
    }

    public static class UserData {
        private String sevarth_id;
        private String email;
        private String name;
        private String role;

        public String getSevarthId() {
            return sevarth_id;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }

        public String getRole() {
            return role;
        }
    }
} 