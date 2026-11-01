package com.aditya.dataautomation.service;

import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.dto.excel.WorkbookSummaryDto;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.aditya.dataautomation.service.entity.WorkingSessionEntity;
import com.aditya.dataautomation.service.repository.WorkingSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkbookSessionHolder {

    private static final int MAX_HISTORY = 25;
    private static final int MAX_WORKBOOKS_PER_USER = 20;
    private static final long SESSION_TTL_MILLIS = 2 * 60 * 60 * 1000L; // 2 hours

    private final WorkingSessionRepository workingSessionRepository;
    private final ObjectMapper objectMapper;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryState {
        private ExcelWorkbookData workbook;
        private String actionDescription;
        private long timestamp;

        public HistoryState(ExcelWorkbookData workbook, String actionDescription) {
            this.workbook = workbook;
            this.actionDescription = actionDescription != null && !actionDescription.isBlank()
                    ? actionDescription
                    : "Data Update";
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Getter
    public static class StoredWorkbookEntry {
        private final String fileId;
        private final String fileName;
        private final long fileSize;
        private final long uploadedAt;
        private final List<HistoryState> history = new ArrayList<>();
        private int currentIndex = -1;

        public StoredWorkbookEntry(String fileId, String fileName, long fileSize, ExcelWorkbookData initialWorkbook, String initialAction) {
            this.fileId = fileId;
            this.fileName = fileName != null ? fileName : "Workbook";
            this.fileSize = fileSize;
            this.uploadedAt = System.currentTimeMillis();
            if (initialWorkbook != null) {
                this.history.add(new HistoryState(initialWorkbook, initialAction));
                this.currentIndex = 0;
            }
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p.getId();
        }
        return 0L;
    }

    private Optional<WorkingSessionEntity> getActiveSession(Long userId) {
        Optional<WorkingSessionEntity> active = workingSessionRepository.findByUserIdAndIsActiveTrue(userId);
        if (active.isPresent()) {
            return active;
        }
        List<WorkingSessionEntity> list = workingSessionRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (!list.isEmpty()) {
            WorkingSessionEntity latest = list.get(list.size() - 1);
            latest.setIsActive(true);
            latest.setLastAccessed(LocalDateTime.now());
            workingSessionRepository.save(latest);
            return Optional.of(latest);
        }
        return Optional.empty();
    }

    private List<HistoryState> deserializeHistory(String historyJson) {
        if (historyJson == null || historyJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(historyJson, new TypeReference<List<HistoryState>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize historyJson: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String serializeHistory(List<HistoryState> history) {
        if (history == null || history.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(history);
        } catch (Exception e) {
            log.warn("Failed to serialize history: {}", e.getMessage());
            return "[]";
        }
    }

    private ExcelWorkbookData deserializeWorkbook(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ExcelWorkbookData.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize workbookDataJson: {}", e.getMessage());
            return null;
        }
    }

    private String serializeWorkbook(ExcelWorkbookData workbook) {
        if (workbook == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(workbook);
        } catch (Exception e) {
            log.warn("Failed to serialize workbook: {}", e.getMessage());
            return "{}";
        }
    }

    // =========================================================================
    // Multi-File Registration & Management API
    // =========================================================================

    @Transactional
    public synchronized String registerNewWorkbook(Long userId, ExcelWorkbookData workbook, String fileName, long fileSize, String actionDescription) {
        if (userId == null) {
            userId = getCurrentUserId();
        }
        String fileId = UUID.randomUUID().toString();

        List<WorkingSessionEntity> existing = workingSessionRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (existing.size() >= MAX_WORKBOOKS_PER_USER) {
            WorkingSessionEntity oldest = existing.get(0);
            workingSessionRepository.delete(oldest);
            existing.remove(0);
        }

        for (WorkingSessionEntity s : existing) {
            if (Boolean.TRUE.equals(s.getIsActive())) {
                s.setIsActive(false);
                workingSessionRepository.save(s);
            }
        }

        List<HistoryState> history = new ArrayList<>();
        int currentIndex = -1;
        String workbookJson = "{}";

        if (workbook != null) {
            history.add(new HistoryState(workbook, actionDescription));
            currentIndex = 0;
            workbookJson = serializeWorkbook(workbook);
        }

        WorkingSessionEntity session = WorkingSessionEntity.builder()
                .id(fileId)
                .userId(userId)
                .fileId(fileId)
                .fileName(fileName != null ? fileName : "Workbook")
                .fileSize(fileSize)
                .isActive(true)
                .workbookDataJson(workbookJson)
                .historyJson(serializeHistory(history))
                .currentIndex(currentIndex)
                .createdAt(LocalDateTime.now())
                .lastAccessed(LocalDateTime.now())
                .build();

        workingSessionRepository.save(session);
        return fileId;
    }

    @Transactional
    public synchronized String registerNewWorkbook(ExcelWorkbookData workbook, String fileName, long fileSize, String actionDescription) {
        return registerNewWorkbook(null, workbook, fileName, fileSize, actionDescription);
    }

    @Transactional(readOnly = true)
    public synchronized List<WorkbookSummaryDto> listWorkbooks() {
        Long userId = getCurrentUserId();
        List<WorkingSessionEntity> sessions = workingSessionRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<WorkbookSummaryDto> result = new ArrayList<>();

        for (WorkingSessionEntity session : sessions) {
            ExcelWorkbookData wb = null;
            if (session.getCurrentIndex() != null && session.getCurrentIndex() >= 0) {
                wb = deserializeWorkbook(session.getWorkbookDataJson());
            }
            int sheetCount = wb != null && wb.getSheets() != null ? wb.getSheets().size() : 0;
            int totalRows = 0;
            List<String> sheetNames = new ArrayList<>();
            if (wb != null && wb.getSheets() != null) {
                for (ExcelSheetData s : wb.getSheets()) {
                    if (s != null) {
                        totalRows += s.getRowCount();
                        sheetNames.add(s.getSheetName());
                    }
                }
            }
            long uploadedAt = session.getCreatedAt() != null
                    ? session.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : System.currentTimeMillis();

            result.add(WorkbookSummaryDto.builder()
                    .fileId(session.getFileId())
                    .fileName(session.getFileName())
                    .fileSize(session.getFileSize() != null ? session.getFileSize() : 0L)
                    .sheetCount(sheetCount)
                    .totalRows(totalRows)
                    .active(Boolean.TRUE.equals(session.getIsActive()))
                    .uploadedAt(uploadedAt)
                    .sheetNames(sheetNames)
                    .build());
        }
        return result;
    }

    @Transactional
    public synchronized boolean setActiveWorkbook(String fileId) {
        if (fileId == null) return false;
        Long userId = getCurrentUserId();
        Optional<WorkingSessionEntity> targetOpt = workingSessionRepository.findByUserIdAndFileId(userId, fileId);
        if (targetOpt.isEmpty()) {
            return false;
        }

        List<WorkingSessionEntity> all = workingSessionRepository.findByUserId(userId);
        for (WorkingSessionEntity s : all) {
            boolean active = s.getFileId().equals(fileId);
            if (!Objects.equals(s.getIsActive(), active)) {
                s.setIsActive(active);
                s.setLastAccessed(LocalDateTime.now());
                workingSessionRepository.save(s);
            }
        }
        return true;
    }

    @Transactional
    public synchronized boolean deleteWorkbook(String fileId) {
        if (fileId == null) return false;
        Long userId = getCurrentUserId();
        Optional<WorkingSessionEntity> targetOpt = workingSessionRepository.findByUserIdAndFileId(userId, fileId);
        if (targetOpt.isEmpty()) {
            return false;
        }

        WorkingSessionEntity target = targetOpt.get();
        boolean wasActive = Boolean.TRUE.equals(target.getIsActive());
        workingSessionRepository.delete(target);

        if (wasActive) {
            List<WorkingSessionEntity> remaining = workingSessionRepository.findByUserIdOrderByCreatedAtAsc(userId);
            if (!remaining.isEmpty()) {
                WorkingSessionEntity nextActive = remaining.get(remaining.size() - 1);
                nextActive.setIsActive(true);
                nextActive.setLastAccessed(LocalDateTime.now());
                workingSessionRepository.save(nextActive);
            }
        }
        return true;
    }

    @Transactional(readOnly = true)
    public synchronized String getActiveFileId() {
        Long userId = getCurrentUserId();
        return getActiveSession(userId).map(WorkingSessionEntity::getFileId).orElse(null);
    }

    @Transactional(readOnly = true)
    public synchronized ExcelWorkbookData getWorkbook(String fileId) {
        if (fileId == null) {
            return get();
        }
        Long userId = getCurrentUserId();
        Optional<WorkingSessionEntity> sessionOpt = workingSessionRepository.findByUserIdAndFileId(userId, fileId);
        if (sessionOpt.isPresent()) {
            WorkingSessionEntity s = sessionOpt.get();
            if (s.getCurrentIndex() != null && s.getCurrentIndex() >= 0) {
                return deserializeWorkbook(s.getWorkbookDataJson());
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public synchronized ExcelWorkbookData getWorkbookForUser(Long userId, String fileId) {
        if (userId == null) userId = 0L;
        if (fileId == null) {
            Optional<WorkingSessionEntity> active = getActiveSession(userId);
            if (active.isPresent() && active.get().getCurrentIndex() != null && active.get().getCurrentIndex() >= 0) {
                return deserializeWorkbook(active.get().getWorkbookDataJson());
            }
            return null;
        }
        Optional<WorkingSessionEntity> sessionOpt = workingSessionRepository.findByUserIdAndFileId(userId, fileId);
        if (sessionOpt.isPresent()) {
            WorkingSessionEntity s = sessionOpt.get();
            if (s.getCurrentIndex() != null && s.getCurrentIndex() >= 0) {
                return deserializeWorkbook(s.getWorkbookDataJson());
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public synchronized boolean userOwnsWorkbook(Long userId, String fileId) {
        if (userId == null) userId = 0L;
        if (fileId == null) return false;
        return workingSessionRepository.existsByUserIdAndFileId(userId, fileId);
    }

    // =========================================================================
    // Backward-Compatible Active Workbook Operations (used by cleaning, export, etc.)
    // =========================================================================

    @Transactional
    public synchronized void store(ExcelWorkbookData workbook) {
        store(workbook, "Data Operation");
    }

    @Transactional
    public synchronized void store(ExcelWorkbookData workbook, String actionDescription) {
        if (workbook == null) {
            clear();
            return;
        }
        Long userId = getCurrentUserId();
        Optional<WorkingSessionEntity> sessionOpt = getActiveSession(userId);
        if (sessionOpt.isPresent()) {
            pushStateToSession(sessionOpt.get(), workbook, actionDescription);
        } else {
            String fileName = workbook.getFileName() != null ? workbook.getFileName() : "Active Workbook";
            registerNewWorkbook(userId, workbook, fileName, 0L, actionDescription);
        }
    }

    private void pushStateToSession(WorkingSessionEntity session, ExcelWorkbookData workbook, String actionDescription) {
        List<HistoryState> history = deserializeHistory(session.getHistoryJson());
        int currentIndex = session.getCurrentIndex() != null ? session.getCurrentIndex() : -1;

        while (history.size() > currentIndex + 1) {
            history.remove(history.size() - 1);
        }
        history.add(new HistoryState(workbook, actionDescription));
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
        currentIndex = history.size() - 1;

        session.setCurrentIndex(currentIndex);
        session.setWorkbookDataJson(serializeWorkbook(workbook));
        session.setHistoryJson(serializeHistory(history));
        if (session.getFileName() == null || session.getFileName().isBlank() || "Workbook".equals(session.getFileName())) {
            if (workbook.getFileName() != null && !workbook.getFileName().isBlank()) {
                session.setFileName(workbook.getFileName());
            }
        }
        session.setLastAccessed(LocalDateTime.now());
        workingSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public synchronized ExcelWorkbookData get() {
        Long userId = getCurrentUserId();
        Optional<WorkingSessionEntity> sessionOpt = getActiveSession(userId);
        if (sessionOpt.isPresent()) {
            WorkingSessionEntity s = sessionOpt.get();
            if (s.getCurrentIndex() != null && s.getCurrentIndex() >= 0) {
                return deserializeWorkbook(s.getWorkbookDataJson());
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public synchronized boolean canUndo() {
        Long userId = getCurrentUserId();
        return getActiveSession(userId)
                .map(s -> s.getCurrentIndex() != null && s.getCurrentIndex() > 0)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public synchronized boolean canRedo() {
        Long userId = getCurrentUserId();
        return getActiveSession(userId)
                .map(s -> {
                    if (s.getCurrentIndex() == null || s.getCurrentIndex() < 0) return false;
                    List<HistoryState> h = deserializeHistory(s.getHistoryJson());
                    return s.getCurrentIndex() < h.size() - 1;
                })
                .orElse(false);
    }

    @Transactional
    public synchronized HistoryState undo() {
        if (!canUndo()) return null;
        Long userId = getCurrentUserId();
        WorkingSessionEntity session = getActiveSession(userId).orElse(null);
        if (session == null) return null;

        List<HistoryState> history = deserializeHistory(session.getHistoryJson());
        int newIndex = session.getCurrentIndex() - 1;
        if (newIndex < 0 || newIndex >= history.size()) return null;

        HistoryState targetState = history.get(newIndex);
        session.setCurrentIndex(newIndex);
        session.setWorkbookDataJson(serializeWorkbook(targetState.getWorkbook()));
        session.setLastAccessed(LocalDateTime.now());
        workingSessionRepository.save(session);
        return targetState;
    }

    @Transactional
    public synchronized HistoryState redo() {
        if (!canRedo()) return null;
        Long userId = getCurrentUserId();
        WorkingSessionEntity session = getActiveSession(userId).orElse(null);
        if (session == null) return null;

        List<HistoryState> history = deserializeHistory(session.getHistoryJson());
        int newIndex = session.getCurrentIndex() + 1;
        if (newIndex >= history.size()) return null;

        HistoryState targetState = history.get(newIndex);
        session.setCurrentIndex(newIndex);
        session.setWorkbookDataJson(serializeWorkbook(targetState.getWorkbook()));
        session.setLastAccessed(LocalDateTime.now());
        workingSessionRepository.save(session);
        return targetState;
    }

    @Transactional(readOnly = true)
    public synchronized String getUndoAction() {
        if (!canUndo()) return null;
        Long userId = getCurrentUserId();
        return getActiveSession(userId)
                .map(s -> {
                    List<HistoryState> h = deserializeHistory(s.getHistoryJson());
                    int idx = s.getCurrentIndex();
                    return (idx >= 0 && idx < h.size()) ? h.get(idx).getActionDescription() : null;
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public synchronized String getRedoAction() {
        if (!canRedo()) return null;
        Long userId = getCurrentUserId();
        return getActiveSession(userId)
                .map(s -> {
                    List<HistoryState> h = deserializeHistory(s.getHistoryJson());
                    int idx = s.getCurrentIndex() + 1;
                    return (idx >= 0 && idx < h.size()) ? h.get(idx).getActionDescription() : null;
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public synchronized int getCurrentIndex() {
        Long userId = getCurrentUserId();
        return getActiveSession(userId)
                .map(s -> s.getCurrentIndex() != null ? s.getCurrentIndex() : -1)
                .orElse(-1);
    }

    @Transactional(readOnly = true)
    public synchronized int getHistorySize() {
        Long userId = getCurrentUserId();
        return getActiveSession(userId)
                .map(s -> deserializeHistory(s.getHistoryJson()).size())
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public synchronized List<String> getHistoryDescriptions() {
        Long userId = getCurrentUserId();
        return getActiveSession(userId)
                .map(s -> {
                    List<HistoryState> history = deserializeHistory(s.getHistoryJson());
                    int currentIndex = s.getCurrentIndex() != null ? s.getCurrentIndex() : -1;
                    List<String> list = new ArrayList<>();
                    for (int i = 0; i < history.size(); i++) {
                        list.add(history.get(i).getActionDescription() + (i == currentIndex ? " (Active)" : ""));
                    }
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    @Transactional
    public synchronized void clear() {
        Long userId = getCurrentUserId();
        Optional<WorkingSessionEntity> sessionOpt = getActiveSession(userId);
        if (sessionOpt.isPresent()) {
            WorkingSessionEntity s = sessionOpt.get();
            s.setHistoryJson("[]");
            s.setCurrentIndex(-1);
            s.setWorkbookDataJson("{}");
            s.setLastAccessed(LocalDateTime.now());
            workingSessionRepository.save(s);
        }
    }

    @Transactional
    public synchronized void clearAll() {
        workingSessionRepository.deleteAll();
    }

    @Transactional
    public synchronized void storeForUser(Long userId, ExcelWorkbookData workbook, String actionDescription) {
        if (userId == null) userId = 0L;
        if (workbook == null) {
            workingSessionRepository.deleteAllByUserId(userId);
            return;
        }
        Optional<WorkingSessionEntity> active = getActiveSession(userId);
        if (active.isPresent()) {
            pushStateToSession(active.get(), workbook, actionDescription);
        } else {
            String fileName = workbook.getFileName() != null ? workbook.getFileName() : "Active Workbook";
            registerNewWorkbook(userId, workbook, fileName, 0L, actionDescription);
        }
    }

    @Transactional(readOnly = true)
    public synchronized ExcelWorkbookData getForUser(Long userId) {
        if (userId == null) userId = 0L;
        Optional<WorkingSessionEntity> active = getActiveSession(userId);
        if (active.isPresent() && active.get().getCurrentIndex() != null && active.get().getCurrentIndex() >= 0) {
            return deserializeWorkbook(active.get().getWorkbookDataJson());
        }
        return null;
    }

    @Scheduled(fixedRate = 1800000)
    @Transactional
    public void cleanupInactiveSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        try {
            workingSessionRepository.deleteByLastAccessedBefore(cutoff);
            log.debug("Expired working sessions cleaned up before {}", cutoff);
        } catch (Exception e) {
            log.error("Failed to clean up expired working sessions", e);
        }
    }
}
