package com.aditya.dataautomation.dashboard.service;

import com.aditya.dataautomation.dashboard.dto.CollaboratorResponse;
import com.aditya.dataautomation.dashboard.dto.ShareDashboardRequest;
import com.aditya.dataautomation.dashboard.dto.UpdatePermissionsRequest;

import java.util.List;

public interface DashboardCollaborationService {

    CollaboratorResponse shareDashboard(Long currentUserId, String currentUserEmail, ShareDashboardRequest request);

    List<CollaboratorResponse> listSharedWithMe(Long currentUserId, String currentUserEmail);

    List<CollaboratorResponse> listSharedByMe(Long currentUserId);

    List<CollaboratorResponse> listDashboardCollaborators(Long currentUserId, String currentUserEmail, String dashboardId);

    CollaboratorResponse updatePermissions(Long currentUserId, String currentUserEmail, String shareId, UpdatePermissionsRequest request);

    void revokeShare(Long currentUserId, String currentUserEmail, String shareId);

    void validateDeletePermission(Long currentUserId, String currentUserEmail, String dashboardId);
}
