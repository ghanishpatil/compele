package com.example.mystartup.api;

public class AdminRegistrationRequest {
    private String organization_name;
    private String first_name;
    private String last_name;
    private String sevarth_id;
    private String email;
    private String contact_number;
    private String password;

    public AdminRegistrationRequest(String organizationName, String firstName, String lastName,
                                  String sevarthId, String email, String contactNumber, String password) {
        this.organization_name = organizationName;
        this.first_name = firstName;
        this.last_name = lastName;
        this.sevarth_id = sevarthId;
        this.email = email;
        this.contact_number = contactNumber;
        this.password = password;
    }
} 