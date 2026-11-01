package com.aditya.dataautomation.admin.controller;

import com.aditya.dataautomation.admin.dto.AdminStatsDto;
import com.aditya.dataautomation.admin.dto.AdminUserDto;
import com.aditya.dataautomation.admin.dto.UpdateRoleRequest;
import com.aditya.dataautomation.admin.dto.UpdateStatusRequest;
import com.aditya.dataautomation.admin.service.AdminService;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "Admin user management and system metrics")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @Operation(summary = "List registered platform users with bounded pagination")
    public ResponseEntity<List<AdminUserDto>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(adminService.listUsers(page, size));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get platform usage and account statistics")
    public ResponseEntity<AdminStatsDto> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @RequestMapping(value = "/users/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @Operation(summary = "Enable or disable user account access")
    public ResponseEntity<AdminUserDto> updateStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        AdminUserDto dto = adminService.setUserStatus(principal.getId(), id, request.getEnabled());
        return ResponseEntity.ok(dto);
    }

    @RequestMapping(value = "/users/{id}/role", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @Operation(summary = "Assign role (ROLE_USER or ROLE_ADMIN) to a user")
    public ResponseEntity<AdminUserDto> updateRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        AdminUserDto dto = adminService.setUserRole(principal.getId(), id, request.getRole());
        return ResponseEntity.ok(dto);
    }
}
