package com.aditya.dataautomation.project.service;

import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.dto.excel.ExcelFileResponse;
import com.aditya.dataautomation.dto.excel.ExcelSheetResponse;
import com.aditya.dataautomation.exception.InvalidRequestException;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.aditya.dataautomation.project.dto.ProjectDetailDto;
import com.aditya.dataautomation.project.dto.ProjectSummaryDto;
import com.aditya.dataautomation.project.dto.RenameProjectRequest;
import com.aditya.dataautomation.project.dto.SaveProjectRequest;
import com.aditya.dataautomation.project.entity.Project;
import com.aditya.dataautomation.project.repository.ProjectRepository;
import com.aditya.dataautomation.service.WorkbookSessionHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkbookSessionHolder workbookSessionHolder;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProjectSummaryDto saveCurrentProject(Long userId, SaveProjectRequest request) {
        ExcelWorkbookData workbook = workbookSessionHolder.get();
        if (workbook == null) {
            throw new InvalidRequestException("No active workbook session to save. Please upload a file first.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRequestException("User not found"));

        Project project;
        if (request.getProjectId() != null && !request.getProjectId().isBlank()) {
            project = projectRepository.findByIdAndUserId(request.getProjectId(), userId)
                    .orElseThrow(() -> new InvalidRequestException("Project not found or access denied"));
        } else {
            project = new Project();
            project.setId(UUID.randomUUID().toString());
            project.setUser(user);
        }

        project.setName(request.getName().trim());
        project.setDescription(request.getDescription());
        project.setFileName(workbook.getFileName() != null ? workbook.getFileName() : "Untitled.xlsx");

        int sheetCount = workbook.getSheets() != null ? workbook.getSheets().size() : 0;
        int rowCount = 0;
        int columnCount = 0;
        String activeSheetName = request.getActiveSheetName();

        if (sheetCount > 0) {
            ExcelSheetData firstSheet = workbook.getSheets().get(0);
            if (activeSheetName != null && !activeSheetName.isBlank()) {
                ExcelSheetData sheet = workbook.getSheet(activeSheetName);
                if (sheet != null) {
                    rowCount = sheet.getRowCount();
                    columnCount = sheet.getColumnCount();
                } else {
                    rowCount = firstSheet.getRowCount();
                    columnCount = firstSheet.getColumnCount();
                    activeSheetName = firstSheet.getSheetName();
                }
            } else {
                rowCount = firstSheet.getRowCount();
                columnCount = firstSheet.getColumnCount();
                activeSheetName = firstSheet.getSheetName();
            }
        }

        project.setSheetCount(sheetCount);
        project.setRowCount(rowCount);
        project.setColumnCount(columnCount);
        project.setActiveSheetName(activeSheetName);

        try {
            project.setWorkbookDataJson(objectMapper.writeValueAsString(workbook));
            project.setCleaningHistoryJson(objectMapper.writeValueAsString(workbookSessionHolder.getHistoryDescriptions()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize workbook data for project saving", e);
            throw new RuntimeException("Failed to save project data", e);
        }

        project = projectRepository.save(project);
        log.info("Project saved successfully: id={}, name={}, userId={}", project.getId(), project.getName(), userId);

        return toSummaryDto(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> listUserProjects(Long userId) {
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectSummaryDto getProject(Long userId, String projectId) {
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new InvalidRequestException("Project not found or access denied"));
        return toSummaryDto(project);
    }

    @Transactional
    public ProjectDetailDto openProject(Long userId, String projectId) {
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new InvalidRequestException("Project not found or access denied"));

        try {
            ExcelWorkbookData workbook = objectMapper.readValue(project.getWorkbookDataJson(), ExcelWorkbookData.class);
            workbookSessionHolder.clear();
            workbookSessionHolder.store(workbook, "Open Project: " + project.getName());

            List<String> history = Collections.emptyList();
            if (project.getCleaningHistoryJson() != null && !project.getCleaningHistoryJson().isBlank()) {
                try {
                    history = objectMapper.readValue(project.getCleaningHistoryJson(), List.class);
                } catch (Exception ignored) {}
            }

            ExcelFileResponse preview = toPreviewResponse(workbook);

            log.info("Project loaded into workspace: id={}, name={}, userId={}", project.getId(), project.getName(), userId);
            return ProjectDetailDto.builder()
                    .id(project.getId())
                    .name(project.getName())
                    .description(project.getDescription())
                    .fileName(project.getFileName())
                    .activeSheetName(project.getActiveSheetName())
                    .createdAt(project.getCreatedAt())
                    .updatedAt(project.getUpdatedAt())
                    .preview(preview)
                    .historyDescriptions(history)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize workbook data for project: {}", projectId, e);
            throw new RuntimeException("Failed to restore project data", e);
        }
    }

    @Transactional
    public ProjectSummaryDto renameProject(Long userId, String projectId, RenameProjectRequest request) {
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new InvalidRequestException("Project not found or access denied"));

        project.setName(request.getName().trim());
        project = projectRepository.save(project);
        log.info("Project renamed: id={}, newName={}, userId={}", project.getId(), project.getName(), userId);
        return toSummaryDto(project);
    }

    @Transactional
    public void deleteProject(Long userId, String projectId) {
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new InvalidRequestException("Project not found or access denied"));

        projectRepository.delete(project);
        log.info("Project deleted: id={}, userId={}", projectId, userId);
    }

    public ProjectSummaryDto toSummaryDto(Project p) {
        return ProjectSummaryDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .fileName(p.getFileName())
                .fileSizeBytes(p.getFileSizeBytes())
                .rowCount(p.getRowCount())
                .columnCount(p.getColumnCount())
                .sheetCount(p.getSheetCount())
                .activeSheetName(p.getActiveSheetName())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private ExcelFileResponse toPreviewResponse(ExcelWorkbookData workbook) {
        List<ExcelSheetResponse> sheetResponses = new ArrayList<>();
        if (workbook.getSheets() != null) {
            for (ExcelSheetData sheet : workbook.getSheets()) {
                List<Map<String, Object>> previewRows = new ArrayList<>();
                if (sheet.getRows() != null) {
                    int limit = Math.min(sheet.getRows().size(), 50);
                    for (int i = 0; i < limit; i++) {
                        previewRows.add(sheet.getRows().get(i));
                    }
                }
                sheetResponses.add(ExcelSheetResponse.builder()
                        .sheetName(sheet.getSheetName())
                        .rowCount(sheet.getRowCount())
                        .columnCount(sheet.getColumnCount())
                        .headers(sheet.getHeaders() != null ? sheet.getHeaders() : Collections.emptyList())
                        .previewRows(previewRows)
                        .build());
            }
        }
        return ExcelFileResponse.builder()
                .fileName(workbook.getFileName())
                .fileSize(0)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .sheetCount(sheetResponses.size())
                .sheets(sheetResponses)
                .build();
    }
}
