package com.aditya.dataautomation.dashboard.controller;

import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.dashboard.dto.CollaboratorResponse;
import com.aditya.dataautomation.dashboard.dto.ShareDashboardRequest;
import com.aditya.dataautomation.dashboard.dto.UpdatePermissionsRequest;
import com.aditya.dataautomation.dashboard.service.DashboardCollaborationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/v1/dashboard/collaboration", "/api/dashboard/collaboration"})
@RequiredArgsConstructor
@Tag(name = "Dashboard Collaboration", description = "Share dashboards with managers and team members with granular permission control and audit logging")
public class DashboardCollaborationController {

    private final DashboardCollaborationService collaborationService;
    private final UserRepository userRepository;

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required to access dashboard collaboration");
        }
        if (auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        if (auth.getPrincipal() instanceof UserDetails ud) {
            User user = userRepository.findByEmailIgnoreCase(ud.getUsername())
                    .orElseThrow(() -> new AccessDeniedException("User not found: " + ud.getUsername()));
            return UserPrincipal.create(user);
        }
        throw new AccessDeniedException("Valid user identity required");
    }

    @PostMapping("/share")
    @Operation(summary = "Share dashboard with a manager or colleague")
    public ResponseEntity<CollaboratorResponse> shareDashboard(@Valid @RequestBody ShareDashboardRequest request) {
        UserPrincipal principal = getPrincipal();
        CollaboratorResponse response = collaborationService.shareDashboard(principal.getId(), principal.getEmail(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/shared-with-me")
    @Operation(summary = "List dashboards shared with current user")
    public ResponseEntity<List<CollaboratorResponse>> listSharedWithMe() {
        UserPrincipal principal = getPrincipal();
        return ResponseEntity.ok(collaborationService.listSharedWithMe(principal.getId(), principal.getEmail()));
    }

    @GetMapping("/shared-by-me")
    @Operation(summary = "List dashboards shared by current user")
    public ResponseEntity<List<CollaboratorResponse>> listSharedByMe() {
        UserPrincipal principal = getPrincipal();
        return ResponseEntity.ok(collaborationService.listSharedByMe(principal.getId()));
    }

    @GetMapping("/{dashboardId}/collaborators")
    @Operation(summary = "List all collaborators for a specific dashboard")
    public ResponseEntity<List<CollaboratorResponse>> listDashboardCollaborators(@PathVariable String dashboardId) {
        UserPrincipal principal = getPrincipal();
        return ResponseEntity.ok(collaborationService.listDashboardCollaborators(principal.getId(), principal.getEmail(), dashboardId));
    }

    @PutMapping("/{shareId}/permissions")
    @Operation(summary = "Update collaborator permissions")
    public ResponseEntity<CollaboratorResponse> updatePermissions(
            @PathVariable String shareId,
            @RequestBody UpdatePermissionsRequest request) {
        UserPrincipal principal = getPrincipal();
        return ResponseEntity.ok(collaborationService.updatePermissions(principal.getId(), principal.getEmail(), shareId, request));
    }

    @DeleteMapping("/{shareId}")
    @Operation(summary = "Revoke dashboard share access")
    public ResponseEntity<Map<String, String>> revokeShare(@PathVariable String shareId) {
        UserPrincipal principal = getPrincipal();
        collaborationService.revokeShare(principal.getId(), principal.getEmail(), shareId);
        return ResponseEntity.ok(Map.of("message", "Collaborator access revoked successfully"));
    }

    @GetMapping("/{dashboardId}/validate-delete")
    @Operation(summary = "Validate whether caller is allowed to delete this dashboard")
    public ResponseEntity<Map<String, Boolean>> validateDeletePermission(@PathVariable String dashboardId) {
        UserPrincipal principal = getPrincipal();
        collaborationService.validateDeletePermission(principal.getId(), principal.getEmail(), dashboardId);
        return ResponseEntity.ok(Map.of("canDelete", true));
    }
}
