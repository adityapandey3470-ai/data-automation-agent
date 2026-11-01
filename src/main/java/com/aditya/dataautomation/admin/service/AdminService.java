package com.aditya.dataautomation.admin.service;

import com.aditya.dataautomation.admin.dto.AdminStatsDto;
import com.aditya.dataautomation.admin.dto.AdminUserDto;
import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.exception.InvalidRequestException;
import com.aditya.dataautomation.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

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
    public AdminUserDto setUserStatus(Long currentAdminId, Long targetUserId, boolean enabled) {
        if (currentAdminId.equals(targetUserId)) {
            throw new InvalidRequestException("Administrators cannot disable their own account.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidRequestException("User not found: " + targetUserId));

        user.setEnabled(enabled);
        user = userRepository.save(user);
        log.info("Admin id={} updated user id={} status to enabled={}", currentAdminId, targetUserId, enabled);

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
    public AdminUserDto setUserRole(Long currentAdminId, Long targetUserId, String role) {
        if (!"ROLE_USER".equals(role) && !"ROLE_ADMIN".equals(role)) {
            throw new InvalidRequestException("Invalid role: " + role);
        }

        if (currentAdminId.equals(targetUserId) && "ROLE_USER".equals(role)) {
            throw new InvalidRequestException("Administrators cannot demote themselves to standard user.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidRequestException("User not found: " + targetUserId));

        user.setRole(role);
        user = userRepository.save(user);
        log.info("Admin id={} updated user id={} role to {}", currentAdminId, targetUserId, role);

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
}
