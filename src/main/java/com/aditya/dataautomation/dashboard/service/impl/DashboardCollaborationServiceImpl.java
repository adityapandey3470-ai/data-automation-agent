package com.aditya.dataautomation.dashboard.service.impl;

import com.aditya.dataautomation.admin.entity.AuditLog;
import com.aditya.dataautomation.admin.repository.AuditLogRepository;
import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.dashboard.dto.CollaboratorResponse;
import com.aditya.dataautomation.dashboard.dto.ShareDashboardRequest;
import com.aditya.dataautomation.dashboard.dto.UpdatePermissionsRequest;
import com.aditya.dataautomation.dashboard.entity.DashboardCollaboratorEntity;
import com.aditya.dataautomation.dashboard.repository.DashboardCollaboratorRepository;
import com.aditya.dataautomation.dashboard.repository.DashboardRepository;
import com.aditya.dataautomation.dashboard.service.DashboardCollaborationService;
import com.aditya.dataautomation.dashboard.service.DashboardLiveNotificationService;
import com.aditya.dataautomation.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardCollaborationServiceImpl implements DashboardCollaborationService {

    private static final int MAX_SHARES_PER_DASHBOARD = 50;

    private final DashboardCollaboratorRepository collaboratorRepository;
    private final DashboardRepository dashboardRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final DashboardLiveNotificationService liveNotificationService;

    @Override
    @Transactional
    public CollaboratorResponse shareDashboard(Long currentUserId, String currentUserEmail, ShareDashboardRequest request) {
        String targetEmail = request.getManagerEmail().trim().toLowerCase();
        if (currentUserEmail != null && currentUserEmail.equalsIgnoreCase(targetEmail)) {
            throw new InvalidRequestException("You cannot share a dashboard with yourself");
        }

        // The owner is ALWAYS the user who owns the dashboard row - never inferred from collaborator rows.
        var dashboard = dashboardRepository.findByDashboardId(request.getDashboardId())
                .orElseThrow(() -> new AccessDeniedException("Dashboard not found or access denied"));
        Long realOwnerId = dashboard.getUserId();
        boolean isOwner = realOwnerId != null && realOwnerId.equals(currentUserId);

        List<DashboardCollaboratorEntity> existingShares = collaboratorRepository.findByDashboardIdOrderByCreatedAtDesc(request.getDashboardId());
        DashboardCollaboratorEntity myShare = existingShares.stream()
                .filter(e -> (e.getSharedWithUserId() != null && e.getSharedWithUserId().equals(currentUserId))
                        || (currentUserEmail != null && e.getSharedWithEmail().equalsIgnoreCase(currentUserEmail)))
                .findFirst()
                .orElse(null);

        if (!isOwner && (myShare == null || !myShare.isCanInvite())) {
            throw new AccessDeniedException("You do not have permission to invite others to this dashboard");
        }

        // a delegate can never hand out more rights than they hold themselves
        boolean grantCustomize = request.isCanCustomize();
        boolean grantInvite = request.isCanInvite();
        if (!isOwner) {
            grantCustomize = grantCustomize && myShare.isCanCustomize();
            grantInvite = grantInvite && myShare.isCanInvite();
        }

        Optional<User> targetUser = userRepository.findByEmailIgnoreCase(targetEmail);
        if (targetUser.isPresent() && Objects.equals(targetUser.get().getId(), realOwnerId)) {
            throw new InvalidRequestException("The dashboard owner already has full access");
        }

        Optional<DashboardCollaboratorEntity> existing = collaboratorRepository.findByDashboardIdAndSharedWithEmailIgnoreCase(request.getDashboardId(), targetEmail);
        DashboardCollaboratorEntity entity;
        if (existing.isPresent()) {
            if (!isOwner) {
                throw new AccessDeniedException("Only the dashboard owner can change an existing share");
            }
            entity = existing.get();
            entity.setCanCustomize(grantCustomize);
            entity.setCanInvite(grantInvite);
        } else {
            if (existingShares.size() >= MAX_SHARES_PER_DASHBOARD) {
                throw new InvalidRequestException("This dashboard has reached the maximum of " + MAX_SHARES_PER_DASHBOARD + " collaborators");
            }
            Long targetUserId = targetUser.map(User::getId).orElse(null);
            String status = targetUser.isPresent() ? "ACCEPTED" : "PENDING_REGISTRATION";
            String ownerEmail = userRepository.findById(realOwnerId).map(User::getEmail).orElse("unknown");

            entity = DashboardCollaboratorEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .dashboardId(request.getDashboardId())
                    .ownerId(realOwnerId)
                    .ownerEmail(ownerEmail)
                    .sharedWithEmail(targetEmail)
                    .sharedWithUserId(targetUserId)
                    // trusted name from the database, not from the request (prevents spoofed names in invitations)
                    .dashboardName(dashboard.getName())
                    .canCustomize(grantCustomize)
                    .canInvite(grantInvite)
                    .invitationStatus(status)
                    .build();
        }

        entity = collaboratorRepository.save(entity);

        liveNotificationService.broadcastDashboardUpdate(entity.getDashboardId(), "SHARED", currentUserEmail);

        auditLogRepository.save(AuditLog.builder()
                .adminId(currentUserId)
                .adminEmail(currentUserEmail != null ? currentUserEmail : "system")
                .action("DASHBOARD_SHARE_INVITE")
                .targetResource("dashboard:" + request.getDashboardId())
                .details("Shared dashboard '" + dashboard.getName() + "' with " + targetEmail
                        + " [canCustomize=" + entity.isCanCustomize() + ", canInvite=" + entity.isCanInvite()
                        + ", status=" + entity.getInvitationStatus() + "]")
                .build());

        return toDto(entity);
    }

    @Override
    @Transactional
    public List<CollaboratorResponse> listSharedWithMe(Long currentUserId, String currentUserEmail) {
        if (currentUserEmail != null) {
            List<DashboardCollaboratorEntity> pending = collaboratorRepository.findBySharedWithEmailIgnoreCase(currentUserEmail);
            for (DashboardCollaboratorEntity item : pending) {
                if (item.getSharedWithUserId() == null || "PENDING_REGISTRATION".equals(item.getInvitationStatus())) {
                    item.setSharedWithUserId(currentUserId);
                    item.setInvitationStatus("ACCEPTED");
                    collaboratorRepository.save(item);
                }
            }
        }

        List<DashboardCollaboratorEntity> records = collaboratorRepository.findForUser(currentUserId, currentUserEmail);
        return records.stream()
                .filter(c -> !c.getOwnerId().equals(currentUserId))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollaboratorResponse> listSharedByMe(Long currentUserId) {
        return collaboratorRepository.findByOwnerIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollaboratorResponse> listDashboardCollaborators(Long currentUserId, String currentUserEmail, String dashboardId) {
        List<DashboardCollaboratorEntity> shares = collaboratorRepository.findByDashboardIdOrderByCreatedAtDesc(dashboardId);
        boolean isAuthorized = shares.stream().anyMatch(c ->
                c.getOwnerId().equals(currentUserId)
                || (c.getSharedWithUserId() != null && c.getSharedWithUserId().equals(currentUserId))
                || (c.getSharedWithEmail() != null && c.getSharedWithEmail().equalsIgnoreCase(currentUserEmail)));

        if (!isAuthorized) {
            boolean isOwnerInDb = dashboardRepository.findByDashboardId(dashboardId)
                    .map(d -> d.getUserId() != null && d.getUserId().equals(currentUserId))
                    .orElse(false);
            if (!isOwnerInDb) {
                throw new AccessDeniedException("You do not have permission to view collaborators for this dashboard");
            }
        }

        return shares.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CollaboratorResponse updatePermissions(Long currentUserId, String currentUserEmail, String shareId, UpdatePermissionsRequest request) {
        DashboardCollaboratorEntity entity = collaboratorRepository.findById(shareId)
                .orElseThrow(() -> new InvalidRequestException("Collaborator record not found"));

        Long realOwnerId = dashboardRepository.findByDashboardId(entity.getDashboardId())
                .map(d -> d.getUserId())
                .orElse(null);
        if (realOwnerId == null || !realOwnerId.equals(currentUserId)) {
            throw new AccessDeniedException("Only the dashboard owner can update permissions");
        }

        entity.setCanCustomize(request.isCanCustomize());
        entity.setCanInvite(request.isCanInvite());
        entity = collaboratorRepository.save(entity);

        liveNotificationService.broadcastDashboardUpdate(entity.getDashboardId(), "SHARED", currentUserEmail);

        auditLogRepository.save(AuditLog.builder()
                .adminId(currentUserId)
                .adminEmail(currentUserEmail != null ? currentUserEmail : "system")
                .action("DASHBOARD_PERMISSIONS_UPDATE")
                .targetResource("dashboard:" + entity.getDashboardId())
                .details("Updated permissions for " + entity.getSharedWithEmail() + ": canCustomize=" + entity.isCanCustomize() + ", canInvite=" + entity.isCanInvite())
                .build());

        return toDto(entity);
    }

    @Override
    @Transactional
    public void revokeShare(Long currentUserId, String currentUserEmail, String shareId) {
        DashboardCollaboratorEntity entity = collaboratorRepository.findById(shareId)
                .orElseThrow(() -> new InvalidRequestException("Collaborator record not found"));

        Long realOwnerId = dashboardRepository.findByDashboardId(entity.getDashboardId())
                .map(d -> d.getUserId())
                .orElse(null);
        boolean isOwner = realOwnerId != null && realOwnerId.equals(currentUserId);
        boolean isSelf = (entity.getSharedWithUserId() != null && entity.getSharedWithUserId().equals(currentUserId))
                || entity.getSharedWithEmail().equalsIgnoreCase(currentUserEmail);

        if (!isOwner && !isSelf) {
            throw new AccessDeniedException("You do not have permission to revoke this dashboard share");
        }

        collaboratorRepository.delete(entity);

        liveNotificationService.broadcastDashboardUpdate(entity.getDashboardId(), "REVOKED", currentUserEmail);

        auditLogRepository.save(AuditLog.builder()
                .adminId(currentUserId)
                .adminEmail(currentUserEmail != null ? currentUserEmail : "system")
                .action("DASHBOARD_SHARE_REVOKED")
                .targetResource("dashboard:" + entity.getDashboardId())
                .details("Revoked access for " + entity.getSharedWithEmail() + " to dashboard '" + entity.getDashboardName() + "'")
                .build());
    }

    @Override
    @Transactional
    public void validateDeletePermission(Long currentUserId, String currentUserEmail, String dashboardId) {
        List<DashboardCollaboratorEntity> records = collaboratorRepository.findByDashboardIdOrderByCreatedAtDesc(dashboardId);
        if (records.isEmpty()) {
            return;
        }

        Long realOwnerId = dashboardRepository.findByDashboardId(dashboardId)
                .map(d -> d.getUserId())
                .orElse(null);
        boolean isOwner = realOwnerId != null && realOwnerId.equals(currentUserId);
        if (isOwner) {
            return;
        }

        boolean isCollaborator = records.stream().anyMatch(r ->
                (r.getSharedWithUserId() != null && r.getSharedWithUserId().equals(currentUserId))
                        || r.getSharedWithEmail().equalsIgnoreCase(currentUserEmail));

        if (isCollaborator) {
            auditLogRepository.save(AuditLog.builder()
                    .adminId(currentUserId)
                    .adminEmail(currentUserEmail != null ? currentUserEmail : "collaborator")
                    .action("UNAUTHORIZED_DASHBOARD_DELETE_ATTEMPT")
                    .targetResource("dashboard:" + dashboardId)
                    .details("Blocked unauthorized delete request by manager/collaborator for dashboard: " + dashboardId)
                    .build());

            throw new AccessDeniedException("Collaborators are not permitted to delete shared dashboards. Only the dashboard owner can delete this dashboard.");
        }
    }

    private CollaboratorResponse toDto(DashboardCollaboratorEntity entity) {
        return CollaboratorResponse.builder()
                .id(entity.getId())
                .dashboardId(entity.getDashboardId())
                .ownerId(entity.getOwnerId())
                .ownerEmail(entity.getOwnerEmail())
                .sharedWithEmail(entity.getSharedWithEmail())
                .sharedWithUserId(entity.getSharedWithUserId())
                .dashboardName(entity.getDashboardName())
                .canCustomize(entity.isCanCustomize())
                .canInvite(entity.isCanInvite())
                .invitationStatus(entity.getInvitationStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
