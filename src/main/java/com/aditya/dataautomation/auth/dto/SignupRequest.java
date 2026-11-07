package com.aditya.dataautomation.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    private String confirmPassword;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    public SignupRequest(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.confirmPassword = password;
        this.name = name;
    }
}
