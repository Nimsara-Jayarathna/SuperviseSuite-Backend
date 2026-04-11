package com.supervisesuite.backend.auth.dto;

import com.supervisesuite.backend.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterCompleteRequest {

    @NotBlank
    private String registrationToken;

    @NotBlank
    @Size(max = 100)
    private String fname;

    @NotBlank
    @Size(max = 100)
    private String lname;

    @NotBlank
    @StrongPassword
    private String password;

    @Size(max = 20)
    private String name;

    private String role;

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }

    public String getFname() {
        return fname;
    }

    public void setFname(String fname) {
        this.fname = fname;
    }

    public String getLname() {
        return lname;
    }

    public void setLname(String lname) {
        this.lname = lname;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
