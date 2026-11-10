package com.aditya.dataautomation.admin.service;

import com.aditya.dataautomation.admin.dto.AdminFileDto;
import com.aditya.dataautomation.admin.dto.AdminStatsDto;
import com.aditya.dataautomation.admin.dto.AdminUserDto;
import com.aditya.dataautomation.admin.entity.AuditLog;
import com.aditya.dataautomation.admin.repository.AuditLogRepository;
import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.exception.InvalidRequestException;
import com.aditya.dataautomation.project.entity.Project;
import com.aditya.dataautomation.project.repository.ProjectRepository;
import com.aditya.dataautomation.support.entity.SupportTicket;
import com.aditya.dataautomation.support.repository.SupportTicketRepository;
import com.aditya.dataautomation.workbook.entity.WorkingSessionEntity;
import com.aditya.dataautomation.workbook.repository.WorkingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final WorkingSessionRepository workingSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final com.aditya.dataautomation.admin.repository.AiUsageMetricRepository aiUsageMetricRepository;
    private final com.aditya.dataautomation.job.repository.AsyncJobRepository asyncJobRepository;
    private final com.aditya.dataautomation.dashboard.repository.DashboardRepository dashboardRepository;
    private final com.aditya.dataautomation.dashboard.repository.DashboardCollaboratorRepository dashboardCollaboratorRepository;

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> VALID_TICKET_STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");

    private void recordAudit(Long adminId, String adminEmail, String action, String targetResource, String details) {
        try {
            AuditLog logEntry = AuditLog.builder()
                    .adminId(adminId != null ? adminId : 0L)
                    .adminEmail(adminEmail != null ? adminEmail : "admin@tabufinery.io")
                    .action(action)
                    .targetResource(targetResource)
                    .details(details)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to write audit log for action {}: {}", action, e.getMessage());
            throw new IllegalStateException("Audit log could not be written; the action was rolled back", e);
        }
    }

    /** Throws if {@code target} is the only enabled administrator. Locks all enabled admins first (race-safe). */
    private void assertNotLastAdmin(User target) {
        List<User> admins = userRepository.lockEnabledAdmins();
        boolean targetIsEnabledAdmin = admins.stream().anyMatch(a -> a.getId().equals(target.getId()));
        if (targetIsEnabledAdmin && admins.size() <= 1) {
            throw new InvalidRequestException("This is the last active administrator and cannot be demoted, disabled or deleted.");
        }
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> listUsers() {
        return listUsers(0, DEFAULT_PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> listUsers(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "id"));

        List<User> users = userRepository.findAll(pageable).getContent();
        Map<Long, Integer> counts = new HashMap<>();
        if (!users.isEmpty()) {
            List<Long> ids = users.stream().map(User::getId).collect(Collectors.toList());
            for (Object[] row : projectRepository.countByUserIds(ids)) {
                counts.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
            }
        }
        return users.stream()
                .map(u -> toAdminUserDto(u, counts.getOrDefault(u.getId(), 0)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminStatsDto getStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByEnabled(true);
        long disabledUsers = totalUsers - activeUsers;
        long adminUsers = userRepository.countByRole("ROLE_ADMIN");
        long totalProjects = projectRepository.count();

        long freeUsers = userRepository.countBySubscriptionTier("FREE");
        long proUsers = userRepository.countBySubscriptionTier("PRO");
        long enterpriseUsers = userRepository.countBySubscriptionTier("ENTERPRISE");
        long totalAiQueriesUsed = userRepository.sumAiQueriesUsed();
        long totalTokensTracked = userRepository.sumTotalTokensUsed();

        return AdminStatsDto.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .disabledUsers(disabledUsers)
                .adminUsers(adminUsers)
                .totalProjects(totalProjects)
                .freeUsers(freeUsers)
                .proUsers(proUsers)
                .enterpriseUsers(enterpriseUsers)
                .totalAiQueriesUsed(totalAiQueriesUsed)
                .totalTokensTracked(totalTokensTracked)
                .build();
    }

    @Transactional
    public AdminUserDto setUserStatus(Long currentAdminId, String currentAdminEmail, Long targetUserId, boolean enabled) {
        if (currentAdminId.equals(targetUserId)) {
            throw new InvalidRequestException("Administrators cannot disable their own account.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidRequestException("User not found: " + targetUserId));

        if (!enabled && "ROLE_ADMIN".equals(user.getRole())) {
            assertNotLastAdmin(user);
        }
        user.setEnabled(enabled);
        user = userRepository.save(user);
        log.info("Admin id={} updated user id={} status to enabled={}", currentAdminId, targetUserId, enabled);

        recordAudit(currentAdminId, currentAdminEmail, "USER_STATUS_CHANGE", "user:" + targetUserId,
                (enabled ? "Enabled" : "Disabled") + " account for " + user.getEmail() + " (ID: " + user.getId() + ")");

        return toAdminUserDto(user);
    }

    @Transactional
    public AdminUserDto setUserRole(Long currentAdminId, String currentAdminEmail, Long targetUserId, String role) {
        if (!"ROLE_USER".equals(role) && !"ROLE_ADMIN".equals(role)) {
            throw new InvalidRequestException("Invalid role: " + role);
        }

        if (currentAdminId.equals(targetUserId) && "ROLE_USER".equals(role)) {
            throw new InvalidRequestException("Administrators cannot demote themselves to standard user.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidRequestException("User not found: " + targetUserId));

        if ("ROLE_USER".equals(role) && "ROLE_ADMIN".equals(user.getRole())) {
            assertNotLastAdmin(user);
        }
        String oldRole = user.getRole();
        user.setRole(role);
        user = userRepository.save(user);
        log.info("Admin id={} updated user id={} role from {} to {}", currentAdminId, targetUserId, oldRole, role);

        recordAudit(currentAdminId, currentAdminEmail, "USER_ROLE_CHANGE", "user:" + targetUserId,
                "Changed role for " + user.getEmail() + " from " + oldRole + " to " + role);

        return toAdminUserDto(user);
    }

    @Transactional
    public AdminUserDto setUserSubscription(Long currentAdminId, String currentAdminEmail, Long targetUserId, String rawTier) {
        if (rawTier == null || rawTier.isBlank()) {
            throw new InvalidRequestException("Subscription tier cannot be empty");
        }
        String tier = rawTier.trim().toUpperCase();
        if (!"FREE".equals(tier) && !"PRO".equals(tier) && !"ENTERPRISE".equals(tier)) {
            throw new InvalidRequestException("Invalid subscription tier: " + rawTier + ". Allowed: FREE, PRO, ENTERPRISE");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidRequestException("User not found: " + targetUserId));

        String oldTier = user.getSubscriptionTier() != null ? user.getSubscriptionTier() : "FREE";
        user.setSubscriptionTier(tier);
        if ("FREE".equals(tier)) {
            user.setAiQueriesLimit(10);
        } else if ("PRO".equals(tier)) {
            user.setAiQueriesLimit(500);
        } else if ("ENTERPRISE".equals(tier)) {
            user.setAiQueriesLimit(999999);
        }
        user = userRepository.save(user);

        recordAudit(currentAdminId, currentAdminEmail, "SUBSCRIPTION_CHANGE", "user:" + targetUserId,
                "Changed subscription for " + user.getEmail() + " from " + oldTier + " to " + tier);

        return toAdminUserDto(user);
    }

    @Transactional
    public AdminUserDto resetUserAiQuota(Long currentAdminId, String currentAdminEmail, Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidRequestException("User not found: " + targetUserId));

        int previousUsed = user.getAiQueriesUsed() != null ? user.getAiQueriesUsed() : 0;
        user.setAiQueriesUsed(0);
        user = userRepository.save(user);

        recordAudit(currentAdminId, currentAdminEmail, "QUOTA_RESET", "user:" + targetUserId,
                "Reset AI query quota for " + user.getEmail() + " (previously used: " + previousUsed + ")");

        return toAdminUserDto(user);
    }

    private AdminUserDto toAdminUserDto(User user) {
        return toAdminUserDto(user, (int) projectRepository.countByUserId(user.getId()));
    }

    private AdminUserDto toAdminUserDto(User user, int projectCount) {
        return AdminUserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .subscriptionTier(user.getSubscriptionTier() != null ? user.getSubscriptionTier().toUpperCase() : "FREE")
                .aiQueriesUsed(user.getAiQueriesUsed() != null ? user.getAiQueriesUsed() : 0)
                .aiQueriesLimit(user.getAiQueriesLimit() != null ? user.getAiQueriesLimit() : 10)
                .totalTokensUsed(user.getTotalTokensUsed() != null ? user.getTotalTokensUsed() : 0L)
                .projectCount(projectCount)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> listTickets() {
        return supportTicketRepository.findTop200ByOrderByCreatedAtDesc();
    }

    @Transactional
    public SupportTicket updateTicketStatus(Long currentAdminId, String currentAdminEmail, Long ticketId, String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new InvalidRequestException("Ticket status cannot be empty");
        }
        String status = rawStatus.trim().toUpperCase();
        if (!VALID_TICKET_STATUSES.contains(status)) {
            throw new InvalidRequestException("Invalid ticket status: " + rawStatus + ". Allowed: " + VALID_TICKET_STATUSES);
        }

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new InvalidRequestException("Support ticket not found: " + ticketId));

        String oldStatus = ticket.getStatus();
        ticket.setStatus(status);
        ticket = supportTicketRepository.save(ticket);

        recordAudit(currentAdminId, currentAdminEmail, "TICKET_STATUS_UPDATE", "ticket:" + ticket.getTicketCode(),
                "Updated ticket " + ticket.getTicketCode() + " status from " + oldStatus + " to " + status);

        return ticket;
    }

    @Transactional(readOnly = true)
    public List<AdminFileDto> listAllFiles() {
        List<AdminFileDto> files = new ArrayList<>();
        Map<Long, User> userCache = new HashMap<>();

        List<ProjectRepository.ProjectMetadataView> projects = projectRepository.findAllProjectMetadata();
        for (ProjectRepository.ProjectMetadataView p : projects) {
            ProjectRepository.ProjectMetadataView.UserMetadataView owner = p.getUser();
            files.add(AdminFileDto.builder()
                    .fileId(p.getId())
                    .fileName(p.getFileName() != null ? p.getFileName() : p.getName())
                    .fileSize(p.getFileSizeBytes() != null ? p.getFileSizeBytes() : 0L)
                    .ownerId(owner != null ? owner.getId() : null)
                    .ownerName(owner != null ? owner.getName() : "Unknown")
                    .ownerEmail(owner != null ? owner.getEmail() : "Unknown")
                    .rowCount(p.getRowCount() != null ? p.getRowCount() : 0)
                    .columnCount(p.getColumnCount() != null ? p.getColumnCount() : 0)
                    .uploadedAt(p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt())
                    .source("SAVED_PROJECT")
                    .build());
        }

        List<WorkingSessionRepository.SessionMetadataView> sessions = workingSessionRepository.findAllMetadataOrderByLastAccessedDesc();
        Set<Long> sessionOwnerIds = new HashSet<>();
        for (WorkingSessionRepository.SessionMetadataView s : sessions) {
            if (s.getUserId() != null) {
                sessionOwnerIds.add(s.getUserId());
            }
        }
        if (!sessionOwnerIds.isEmpty()) {
            userRepository.findAllById(sessionOwnerIds).forEach(u -> userCache.put(u.getId(), u));
        }
        for (WorkingSessionRepository.SessionMetadataView s : sessions) {
            Long ownerId = s.getUserId();
            User owner = ownerId != null ? userCache.get(ownerId) : null;
            files.add(AdminFileDto.builder()
                    .fileId(s.getFileId())
                    .fileName(s.getFileName() != null ? s.getFileName() : "Active Workbook")
                    .fileSize(s.getFileSize() != null ? s.getFileSize() : 0L)
                    .ownerId(ownerId)
                    .ownerName(owner != null ? owner.getName() : (ownerId == null || ownerId == 0L ? "Anonymous Guest" : "User #" + ownerId))
                    .ownerEmail(owner != null ? owner.getEmail() : (ownerId == null || ownerId == 0L ? "guest@tabufinery.io" : "user" + ownerId + "@tabufinery.io"))
                    .rowCount(null)
                    .columnCount(null)
                    .uploadedAt(s.getLastAccessed() != null ? s.getLastAccessed() : s.getCreatedAt())
                    .source("WORKING_SESSION")
                    .build());
        }

        files.sort((a, b) -> {
            if (a.getUploadedAt() == null) return 1;
            if (b.getUploadedAt() == null) return -1;
            return b.getUploadedAt().compareTo(a.getUploadedAt());
        });

        return files.size() > 1000 ? new ArrayList<>(files.subList(0, 1000)) : files;
    }

    @Transactional
    public void deleteFile(Long currentAdminId, String currentAdminEmail, String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new InvalidRequestException("File ID cannot be blank");
        }

        boolean found = false;
        String fileName = fileId;
        String ownerInfo = "Unknown";

        if (projectRepository.existsById(fileId)) {
            Project p = projectRepository.findById(fileId).orElse(null);
            if (p != null) {
                fileName = p.getFileName() != null ? p.getFileName() : p.getName();
                ownerInfo = p.getUser() != null ? p.getUser().getEmail() : "Unknown";
                projectRepository.delete(p);
                found = true;
            }
        }

        Optional<WorkingSessionEntity> sessionOpt = workingSessionRepository.findById(fileId);
        if (sessionOpt.isPresent()) {
            WorkingSessionEntity s = sessionOpt.get();
            fileName = s.getFileName() != null ? s.getFileName() : fileName;
            workingSessionRepository.delete(s);
            found = true;
        }

        if (!found) {
            throw new InvalidRequestException("File not found or already deleted: " + fileId);
        }

        recordAudit(currentAdminId, currentAdminEmail, "FILE_DELETE", "file:" + fileId,
                "Deleted file '" + fileName + "' (Owner: " + ownerInfo + ")");
    }

    @Transactional
    public void deleteUser(Long currentAdminId, String currentAdminEmail, Long targetUserId) {
        if (targetUserId == null) {
            throw new InvalidRequestException("Target user ID cannot be null");
        }

        if (Long.valueOf(0L).equals(targetUserId)) {
            throw new InvalidRequestException("Cannot delete the anonymous platform system account.");
        }

        if (currentAdminId != null && currentAdminId.equals(targetUserId)) {
            throw new InvalidRequestException("Administrators cannot delete their own account.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidRequestException("User not found: " + targetUserId));

        if ("ROLE_ADMIN".equals(user.getRole())) {
            assertNotLastAdmin(user);
        }

        String userEmail = user.getEmail();
        String userName = user.getName();

        aiUsageMetricRepository.deleteByUserId(targetUserId);
        asyncJobRepository.deleteAllByOwner(targetUserId);
        dashboardCollaboratorRepository.deleteByOwnerIdOrSharedWithUserId(targetUserId, targetUserId);
        dashboardRepository.deleteByUserId(targetUserId);
        userRepository.delete(user);
        log.info("Admin id={} permanently deleted user id={} ({})", currentAdminId, targetUserId, userEmail);

        recordAudit(currentAdminId, currentAdminEmail, "USER_DELETE", "user:" + targetUserId,
                "Permanently deleted user account " + userEmail + " (Name: " + userName + ", ID: " + targetUserId + ") and all associated datasets.");
    }

    @Transactional
    public void deleteTicket(Long currentAdminId, String currentAdminEmail, Long ticketId) {
        if (ticketId == null) {
            throw new InvalidRequestException("Ticket ID cannot be null");
        }

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new InvalidRequestException("Support ticket not found: " + ticketId));

        String ticketCode = ticket.getTicketCode();
        String userEmail = ticket.getUserEmail();

        supportTicketRepository.delete(ticket);
        log.info("Admin id={} deleted support ticket code={}", currentAdminId, ticketCode);

        recordAudit(currentAdminId, currentAdminEmail, "TICKET_DELETE", "ticket:" + ticketCode,
                "Permanently deleted support ticket " + ticketCode + " (Submitted by: " + userEmail + ")");
    }

    @Transactional
    public int pruneAuditLogs(Long currentAdminId, String currentAdminEmail, Integer days) {
        if (days == null || days < 30) {
            throw new InvalidRequestException("Audit logs can only be pruned if they are older than at least 30 days.");
        }
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(days);
        int deletedCount = auditLogRepository.deleteByCreatedAtBefore(cutoff);

        log.info("Admin id={} pruned {} audit logs older than {} days", currentAdminId, deletedCount, days != null ? days : 0);

        recordAudit(currentAdminId, currentAdminEmail, "AUDIT_PURGE", "system:audit_logs",
                "Pruned " + deletedCount + " security audit log records older than " + (days != null && days > 0 ? days + " days" : "now") + ".");

        return deletedCount;
    }

    @Transactional(readOnly = true)
    public List<AuditLog> listAuditLogs() {
        return auditLogRepository.findTop200ByActionNotLikeOrderByCreatedAtDesc("DASHBOARD_%");
    }
}
