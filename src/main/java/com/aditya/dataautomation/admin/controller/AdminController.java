package com.aditya.dataautomation.admin.controller;

import com.aditya.dataautomation.admin.dto.*;
import com.aditya.dataautomation.admin.entity.AuditLog;
import com.aditya.dataautomation.admin.service.AdminService;
import com.aditya.dataautomation.admin.service.AiUsageService;
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
    private final AiUsageService aiUsageService;

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

    @RequestMapping(value = "/users/{id}/subscription", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @Operation(summary = "Update user subscription tier (FREE, PRO, ENTERPRISE)")
    public ResponseEntity<AdminUserDto> updateSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        AdminUserDto dto = adminService.setUserSubscription(principal.getId(), principal.getUsername(), id, request.getTier());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/users/{id}/reset-quota")
    @Operation(summary = "Reset user AI query quota count back to zero")
    public ResponseEntity<AdminUserDto> resetQuota(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        AdminUserDto dto = adminService.resetUserAiQuota(principal.getId(), principal.getUsername(), id);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Permanently delete a user account and associated resources")
    public ResponseEntity<Void> deleteUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        adminService.deleteUser(principal.getId(), principal.getUsername(), id);
        return ResponseEntity.noContent().build();
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

    @DeleteMapping("/tickets/{id}")
    @Operation(summary = "Permanently delete a support ticket or bug report")
    public ResponseEntity<Void> deleteTicket(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        adminService.deleteTicket(principal.getId(), principal.getUsername(), id);
        return ResponseEntity.noContent().build();
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

    @DeleteMapping("/audit-logs/prune")
    @Operation(summary = "Prune security audit logs older than specified days")
    public ResponseEntity<java.util.Map<String, Object>> pruneAuditLogs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "30") Integer days) {
        int count = adminService.pruneAuditLogs(principal.getId(), principal.getUsername(), days);
        return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "deletedCount", count,
                "message", "Successfully pruned " + count + " audit log records."
        ));
    }

    @GetMapping("/ai-metrics")
    @Operation(summary = "Get AI token usage metrics and operational analytics")
    public ResponseEntity<AiMetricsDto> getAiMetrics() {
        return ResponseEntity.ok(aiUsageService.getMetrics());
    }
}
