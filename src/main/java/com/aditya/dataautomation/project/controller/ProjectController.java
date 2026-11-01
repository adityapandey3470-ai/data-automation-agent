package com.aditya.dataautomation.project.controller;

import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.project.dto.ProjectDetailDto;
import com.aditya.dataautomation.project.dto.ProjectSummaryDto;
import com.aditya.dataautomation.project.dto.RenameProjectRequest;
import com.aditya.dataautomation.project.dto.SaveProjectRequest;
import com.aditya.dataautomation.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Save, load, rename, and delete analysis projects")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping("/save")
    @Operation(summary = "Save or update the current workbook as a project")
    public ResponseEntity<ProjectSummaryDto> saveCurrentProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SaveProjectRequest request) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ProjectSummaryDto summary = projectService.saveCurrentProject(principal.getId(), request);
        return ResponseEntity.ok(summary);
    }

    @GetMapping
    @Operation(summary = "List all saved projects for the authenticated user")
    public ResponseEntity<List<ProjectSummaryDto>> listProjects(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<ProjectSummaryDto> projects = projectService.listUserProjects(principal.getId());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project summary by ID")
    public ResponseEntity<ProjectSummaryDto> getProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ProjectSummaryDto project = projectService.getProject(principal.getId(), id);
        return ResponseEntity.ok(project);
    }

    @PostMapping("/{id}/open")
    @Operation(summary = "Open a saved project into the active workspace")
    public ResponseEntity<ProjectDetailDto> openProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ProjectDetailDto detail = projectService.openProject(principal.getId(), id);
        return ResponseEntity.ok(detail);
    }

    @PutMapping("/{id}/rename")
    @Operation(summary = "Rename a saved project")
    public ResponseEntity<ProjectSummaryDto> renameProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody RenameProjectRequest request) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ProjectSummaryDto summary = projectService.renameProject(principal.getId(), id, request);
        return ResponseEntity.ok(summary);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved project")
    public ResponseEntity<Map<String, String>> deleteProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        projectService.deleteProject(principal.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Project deleted successfully", "id", id));
    }
}
