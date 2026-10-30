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

        return userRepository.findAll(pageable).stream().map(u -> {
            int projectCount = (int) projectRepository.countByUserId(u.getId());
            return AdminUserDto.builder()
                    .id(u.getId())
                    .name(u.getName())
                    .email(u.getEmail())
                    .role(u.getRole())
                    .enabled(u.isEnabled())
                    .projectCount(projectCount)
                    .createdAt(u.getCreatedAt())
                    .updatedAt(u.getUpdatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminStatsDto getStats() {
        List<User> all = userRepository.findAll();
        long totalUsers = all.size();
        long activeUsers = all.stream().filter(User::isEnabled).count();
        long disabledUsers = totalUsers - activeUsers;
        long adminUsers = all.stream().filter(u -> "ROLE_ADMIN".equals(u.getRole())).count();
        long totalProjects = projectRepository.count();

        return AdminStatsDto.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .disabledUsers(disabledUsers)
                .adminUsers(adminUsers)
                .totalProjects(totalProjects)
                .build();
    }

    @Transactional
    public AdminUserDto setUserStatus(Long currentAdminId, String currentAdminEmail, Long targetUserId, boolean enabled) {
        if (currentAdminId.equals(targetUserId)) {
            throw new InvalidRequestException("Administrators cannot disable their own account.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidRequestException("User not found: " + targetUserId));

        user.setEnabled(enabled);
        user = userRepository.save(user);
        log.info("Admin id={} updated user id={} status to enabled={}", currentAdminId, targetUserId, enabled);

        recordAudit(currentAdminId, currentAdminEmail, "USER_STATUS_CHANGE", "user:" + targetUserId,
                (enabled ? "Enabled" : "Disabled") + " account for " + user.getEmail() + " (ID: " + user.getId() + ")");

        int projectCount = (int) projectRepository.countByUserId(user.getId());
        return AdminUserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .projectCount(projectCount)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
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

        String oldRole = user.getRole();
        user.setRole(role);
        user = userRepository.save(user);
        log.info("Admin id={} updated user id={} role from {} to {}", currentAdminId, targetUserId, oldRole, role);

        recordAudit(currentAdminId, currentAdminEmail, "USER_ROLE_CHANGE", "user:" + targetUserId,
                "Changed role for " + user.getEmail() + " from " + oldRole + " to " + role);

        int projectCount = (int) projectRepository.countByUserId(user.getId());
        return AdminUserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .projectCount(projectCount)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> listTickets() {
        return supportTicketRepository.findAllByOrderByCreatedAtDesc();
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

        List<Project> projects = projectRepository.findAll();
        for (Project p : projects) {
            User owner = p.getUser();
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

        List<WorkingSessionEntity> sessions = workingSessionRepository.findAll();
        for (WorkingSessionEntity s : sessions) {
            if (s.getUserId() != null && !userCache.containsKey(s.getUserId())) {
                userRepository.findById(s.getUserId()).ifPresent(u -> userCache.put(s.getUserId(), u));
            }
            User owner = userCache.get(s.getUserId());
            files.add(AdminFileDto.builder()
                    .fileId(s.getFileId())
                    .fileName(s.getFileName() != null ? s.getFileName() : "Active Workbook")
                    .fileSize(s.getFileSize() != null ? s.getFileSize() : 0L)
                    .ownerId(s.getUserId())
                    .ownerName(owner != null ? owner.getName() : (s.getUserId() == 0L ? "Anonymous Guest" : "User #" + s.getUserId()))
                    .ownerEmail(owner != null ? owner.getEmail() : (s.getUserId() == 0L ? "guest@tabufinery.io" : "user" + s.getUserId() + "@tabufinery.io"))
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

        return files;
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

    @Transactional(readOnly = true)
    public List<AuditLog> listAuditLogs() {
        return auditLogRepository.findTop200ByOrderByCreatedAtDesc();
    }
}
