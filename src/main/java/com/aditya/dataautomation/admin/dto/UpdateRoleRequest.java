package com.aditya.dataautomation.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleRequest {
    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(ROLE_USER|ROLE_ADMIN)$", message = "Role must be either ROLE_USER or ROLE_ADMIN")
    private String role;
}
