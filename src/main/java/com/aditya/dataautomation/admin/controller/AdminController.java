package com.aditya.dataautomation.admin.controller;

import com.aditya.dataautomation.admin.dto.*;
import com.aditya.dataautomation.admin.entity.AuditLog;
import com.aditya.dataautomation.admin.service.AdminService;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.support.entity.SupportTicket;
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
        AdminUserDto dto = adminService.setUserStatus(principal.getId(), principal.getUsername(), id, request.getEnabled());
        return ResponseEntity.ok(dto);
    }

    @RequestMapping(value = "/users/{id}/role", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @Operation(summary = "Assign role (ROLE_USER or ROLE_ADMIN) to a user")
    public ResponseEntity<AdminUserDto> updateRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        AdminUserDto dto = adminService.setUserRole(principal.getId(), principal.getUsername(), id, request.getRole());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/tickets")
    @Operation(summary = "List all support tickets and user bug reports")
    public ResponseEntity<List<SupportTicket>> listTickets() {
        return ResponseEntity.ok(adminService.listTickets());
    }

    @RequestMapping(value = "/tickets/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @Operation(summary = "Update support ticket or bug report status")
    public ResponseEntity<SupportTicket> updateTicketStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketStatusRequest request) {
        SupportTicket updated = adminService.updateTicketStatus(principal.getId(), principal.getUsername(), id, request.getStatus());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/files")
    @Operation(summary = "List all uploaded files and datasets across all users")
    public ResponseEntity<List<AdminFileDto>> listFiles() {
        return ResponseEntity.ok(adminService.listAllFiles());
    }

    @DeleteMapping("/files/{fileId}")
    @Operation(summary = "Delete an uploaded file or project dataset")
    public ResponseEntity<Void> deleteFile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String fileId) {
        adminService.deleteFile(principal.getId(), principal.getUsername(), fileId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "List recent administrative security and action logs")
    public ResponseEntity<List<AuditLog>> listAuditLogs() {
        return ResponseEntity.ok(adminService.listAuditLogs());
    }
}
