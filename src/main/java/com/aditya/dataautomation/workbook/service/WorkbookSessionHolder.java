package com.aditya.dataautomation.workbook.service;

import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.excel.dto.WorkbookSummaryDto;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.workbook.entity.WorkingSessionEntity;
import com.aditya.dataautomation.workbook.repository.WorkingSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkbookSessionHolder {

    private static final int MAX_HISTORY = 3;
    private static final int MAX_WORKBOOKS_PER_USER = 20;
    private static final int MAX_CACHED_WORKBOOKS = 15;
    private static final long SESSION_TTL_MILLIS = 2 * 60 * 60 * 1000L;

    private final WorkingSessionRepository workingSessionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.ConcurrentHashMap<Long, Object> userLocks = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CachedWorkbook {
        final Long version;
        final Integer currentIndex;
        final ExcelWorkbookData data;
        final int sheetCount;
        final int totalRows;
        final List<String> sheetNames;

        CachedWorkbook(Long version, Integer currentIndex, ExcelWorkbookData data) {
            this.version = version;
            this.currentIndex = currentIndex;
            this.data = data;
            int count = 0;
            int rows = 0;
            List<String> names = new ArrayList<>();
            if (data != null && data.getSheets() != null) {
                count = data.getSheets().size();
                for (ExcelSheetData s : data.getSheets()) {
                    if (s != null) {
                        rows += s.getRowCount();
                        names.add(s.getSheetName());
                    }
                }
            }
            this.sheetCount = count;
            this.totalRows = rows;
            this.sheetNames = Collections.unmodifiableList(names);
        }
    }

    private static class CachedSummary {
        final Long version;
        final Integer currentIndex;
        final int sheetCount;
        final int totalRows;
        final List<String> sheetNames;

        CachedSummary(Long version, Integer currentIndex, int sheetCount, int totalRows, List<String> sheetNames) {
            this.version = version;
            this.currentIndex = currentIndex;
            this.sheetCount = sheetCount;
            this.totalRows = totalRows;
            this.sheetNames = sheetNames;
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<String, CachedWorkbook> workbookCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, CachedSummary> summaryCache = new java.util.concurrent.ConcurrentHashMap<>();

    private ExcelWorkbookData getOrDeserializeWorkbook(WorkingSessionEntity session) {
        if (session == null || session.getCurrentIndex() == null || session.getCurrentIndex() < 0) {
            return null;
        }
        String fileId = session.getFileId();
        Long version = session.getVersion();
        Integer currentIndex = session.getCurrentIndex();

        CachedWorkbook cached = workbookCache.get(fileId);
        if (cached != null && Objects.equals(cached.version, version) && Objects.equals(cached.currentIndex, currentIndex)) {
            return cached.data;
        }

        ExcelWorkbookData deserialized = deserializeWorkbook(session.getWorkbookDataJson());
        if (deserialized != null) {
            if (workbookCache.size() >= MAX_CACHED_WORKBOOKS) {
                var it = workbookCache.keySet().iterator();
                if (it.hasNext()) {
                    String oldestKey = it.next();
                    workbookCache.remove(oldestKey);
                    summaryCache.remove(oldestKey);
                }
            }
            CachedWorkbook cw = new CachedWorkbook(version, currentIndex, deserialized);
            workbookCache.put(fileId, cw);
            summaryCache.put(fileId, new CachedSummary(version, currentIndex, cw.sheetCount, cw.totalRows, cw.sheetNames));
        }
        return deserialized;
    }

    private Object getLock(Long userId) {
        if (userId == null) return this;
        return userLocks.computeIfAbsent(userId, k -> new Object());
    }

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
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        if (auth.getPrincipal() instanceof UserPrincipal p) {
            return p.getId();
        }
        if (auth.getPrincipal() instanceof UserDetails ud) {
            return userRepository.findByEmailIgnoreCase(ud.getUsername())
                    .map(User::getId)
                    .orElse(null);
        }
        return null;
    }

    private Optional<WorkingSessionEntity> getActiveSession(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
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

    @Transactional
    public String registerNewWorkbook(Long userId, ExcelWorkbookData workbook, String fileName, long fileSize, String actionDescription) {
        if (userId == null) {
            userId = getCurrentUserId();
        }
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to register workbooks");
        }
        synchronized (getLock(userId)) {
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

            session = workingSessionRepository.save(session);
            if (workbook != null) {
                CachedWorkbook cw = new CachedWorkbook(session.getVersion(), currentIndex, workbook);
                workbookCache.put(fileId, cw);
                summaryCache.put(fileId, new CachedSummary(session.getVersion(), currentIndex, cw.sheetCount, cw.totalRows, cw.sheetNames));
            }
            return fileId;
        }
    }

    @Transactional
    public String registerNewWorkbook(ExcelWorkbookData workbook, String fileName, long fileSize, String actionDescription) {
        return registerNewWorkbook(null, workbook, fileName, fileSize, actionDescription);
    }

    @Transactional(readOnly = true)
    public List<WorkbookSummaryDto> listWorkbooks() {
        return listWorkbooks(getCurrentUserId());
    }

    public List<WorkbookSummaryDto> listWorkbooks(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<WorkingSessionRepository.SessionMetadataView> sessions = workingSessionRepository.findMetadataByUserIdOrderByCreatedAtAsc(userId);
        List<WorkbookSummaryDto> result = new ArrayList<>();

        for (WorkingSessionRepository.SessionMetadataView session : sessions) {
            String fileId = session.getFileId();
            Long version = session.getVersion();
            Integer currentIndex = session.getCurrentIndex();
            CachedWorkbook cached = workbookCache.get(fileId);

            int sheetCount;
            int totalRows;
            List<String> sheetNames;

            if (cached != null && Objects.equals(cached.version, version) && Objects.equals(cached.currentIndex, currentIndex)) {
                sheetCount = cached.sheetCount;
                totalRows = cached.totalRows;
                sheetNames = cached.sheetNames;
            } else {
                CachedSummary summary = summaryCache.get(fileId);
                if (summary != null && Objects.equals(summary.version, version) && Objects.equals(summary.currentIndex, currentIndex)) {
                    sheetCount = summary.sheetCount;
                    totalRows = summary.totalRows;
                    sheetNames = summary.sheetNames;
                } else {
                    Optional<WorkingSessionEntity> entityOpt = workingSessionRepository.findByUserIdAndFileId(userId, fileId);
                    ExcelWorkbookData wb = entityOpt.map(this::getOrDeserializeWorkbook).orElse(null);
                    cached = workbookCache.get(fileId);
                    if (cached != null) {
                        sheetCount = cached.sheetCount;
                        totalRows = cached.totalRows;
                        sheetNames = cached.sheetNames;
                    } else {
                        sheetCount = wb != null && wb.getSheets() != null ? wb.getSheets().size() : 0;
                        totalRows = 0;
                        sheetNames = new ArrayList<>();
                        if (wb != null && wb.getSheets() != null) {
                            for (ExcelSheetData s : wb.getSheets()) {
                                if (s != null) {
                                    totalRows += s.getRowCount();
                                    sheetNames.add(s.getSheetName());
                                }
                            }
                        }
                    }
                    summaryCache.put(fileId, new CachedSummary(version, currentIndex, sheetCount, totalRows, sheetNames));
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

    public Map<String, Object> selectAndGetStatus(String fileId) {
        if (fileId == null) return null;
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        synchronized (getLock(userId)) {
            Optional<WorkingSessionEntity> targetOpt = workingSessionRepository.findByUserIdAndFileId(userId, fileId);
            if (targetOpt.isEmpty()) {
                return null;
            }
            workingSessionRepository.switchActiveSession(userId, fileId, LocalDateTime.now());
            WorkingSessionEntity target = targetOpt.get();
            target.setIsActive(true);
            return buildStatusMapForSession(target, userId);
        }
    }

    public boolean setActiveWorkbook(String fileId) {
        if (fileId == null) return false;
        Long userId = getCurrentUserId();
        if (userId == null) return false;
        synchronized (getLock(userId)) {
            if (!workingSessionRepository.existsByUserIdAndFileId(userId, fileId)) {
                return false;
            }
            workingSessionRepository.switchActiveSession(userId, fileId, LocalDateTime.now());
            return true;
        }
    }

    public Map<String, Object> getStatusMap() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            Map<String, Object> emptyResp = new LinkedHashMap<>();
            emptyResp.put("loaded", false);
            emptyResp.put("canUndo", false);
            emptyResp.put("canRedo", false);
            emptyResp.put("fileId", null);
            emptyResp.put("files", Collections.emptyList());
            return emptyResp;
        }
        Optional<WorkingSessionEntity> active = getActiveSession(userId);
        if (active.isEmpty()) {
            Map<String, Object> emptyResp = new LinkedHashMap<>();
            emptyResp.put("loaded", false);
            emptyResp.put("canUndo", false);
            emptyResp.put("canRedo", false);
            emptyResp.put("fileId", null);
            emptyResp.put("files", listWorkbooks(userId));
            return emptyResp;
        }
        return buildStatusMapForSession(active.get(), userId);
    }

    private Map<String, Object> buildStatusMapForSession(WorkingSessionEntity session, Long userId) {
        ExcelWorkbookData workbook = getOrDeserializeWorkbook(session);
        List<Map<String, Object>> sheetSummaries = new ArrayList<>();
        if (workbook != null && workbook.getSheets() != null) {
            for (ExcelSheetData sheet : workbook.getSheets()) {
                if (sheet == null) continue;
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("sheetName", sheet.getSheetName() != null ? sheet.getSheetName() : "");
                info.put("rowCount", sheet.getRowCount());
                info.put("columnCount", sheet.getColumnCount());
                info.put("headers", sheet.getHeaders() != null ? sheet.getHeaders() : Collections.emptyList());
                sheetSummaries.add(info);
            }
        }

        List<HistoryState> history = deserializeHistory(session.getHistoryJson());
        int currentIndex = session.getCurrentIndex() != null ? session.getCurrentIndex() : -1;
        boolean canUndo = currentIndex > 0;
        boolean canRedo = currentIndex >= 0 && currentIndex < history.size() - 1;
        String undoAction = (canUndo && currentIndex < history.size())
                ? history.get(currentIndex).getActionDescription()
                : "";
        String redoAction = (canRedo && currentIndex + 1 < history.size())
                ? history.get(currentIndex + 1).getActionDescription()
                : "";

        Map<String, Object> previewMap = null;
        if (workbook != null && workbook.getSheets() != null && !workbook.getSheets().isEmpty()) {
            ExcelSheetData firstSheet = workbook.getSheets().get(0);
            if (firstSheet != null) {
                List<Map<String, Object>> allRows = firstSheet.getRows() != null ? firstSheet.getRows() : Collections.emptyList();
                List<Map<String, Object>> previewRows = allRows.subList(0, Math.min(100, allRows.size()));
                previewMap = new LinkedHashMap<>();
                previewMap.put("sheetName", firstSheet.getSheetName());
                previewMap.put("headers", firstSheet.getHeaders() != null ? firstSheet.getHeaders() : Collections.emptyList());
                previewMap.put("totalRows", allRows.size());
                previewMap.put("previewRows", previewRows.size());
                previewMap.put("rows", previewRows);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loaded", workbook != null);
        response.put("fileId", session.getFileId());
        response.put("version", session.getVersion());
        response.put("fileName", session.getFileName() != null ? session.getFileName() : "Active Workbook");
        response.put("sheets", sheetSummaries);
        response.put("preview", previewMap);
        response.put("canUndo", canUndo);
        response.put("canRedo", canRedo);
        response.put("undoAction", undoAction != null ? undoAction : "");
        response.put("redoAction", redoAction != null ? redoAction : "");
        response.put("files", listWorkbooks(userId));
        return response;
    }

    public boolean deleteWorkbook(String fileId) {
        if (fileId == null) return false;
        Long userId = getCurrentUserId();
        if (userId == null) return false;
        synchronized (getLock(userId)) {
            Optional<WorkingSessionEntity> targetOpt = workingSessionRepository.findByUserIdAndFileId(userId, fileId);
            if (targetOpt.isEmpty()) {
                return false;
            }

            WorkingSessionEntity target = targetOpt.get();
            boolean wasActive = Boolean.TRUE.equals(target.getIsActive());
            workingSessionRepository.delete(target);
            workbookCache.remove(fileId);
            summaryCache.remove(fileId);

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
    }

    @Transactional(readOnly = true)
    public String getActiveFileId() {
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        return getActiveSession(userId).map(WorkingSessionEntity::getFileId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Long getActiveVersion() {
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        return getActiveSession(userId).map(WorkingSessionEntity::getVersion).orElse(null);
    }

    @Transactional(readOnly = true)
    public ExcelWorkbookData getWorkbook(String fileId) {
        if (fileId == null) {
            return get();
        }
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        Optional<WorkingSessionEntity> sessionOpt = workingSessionRepository.findByUserIdAndFileId(userId, fileId);
        return sessionOpt.map(this::getOrDeserializeWorkbook).orElse(null);
    }

    @Transactional(readOnly = true)
    public ExcelWorkbookData getWorkbookForUser(Long userId, String fileId) {
        if (userId == null) return null;
        if (fileId == null) {
            return getActiveSession(userId).map(this::getOrDeserializeWorkbook).orElse(null);
        }
        return workingSessionRepository.findByUserIdAndFileId(userId, fileId)
                .map(this::getOrDeserializeWorkbook)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean userOwnsWorkbook(Long userId, String fileId) {
        if (userId == null || fileId == null) return false;
        return workingSessionRepository.existsByUserIdAndFileId(userId, fileId);
    }

    @Transactional
    public void store(ExcelWorkbookData workbook) {
        store(workbook, "Data Operation");
    }

    @Transactional
    public void store(ExcelWorkbookData workbook, String actionDescription) {
        if (workbook == null) {
            clear();
            return;
        }
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to modify workbook");
        }
        synchronized (getLock(userId)) {
            Optional<WorkingSessionEntity> sessionOpt = getActiveSession(userId);
            if (sessionOpt.isPresent()) {
                pushStateToSession(sessionOpt.get(), workbook, actionDescription);
            } else {
                String fileName = workbook.getFileName() != null ? workbook.getFileName() : "Active Workbook";
                registerNewWorkbook(userId, workbook, fileName, 0L, actionDescription);
            }
        }
    }

    /** Like store(), but rejects the write if the workbook changed after the client loaded it (409). */
    @Transactional
    public void storeIfVersionMatches(ExcelWorkbookData workbook, String actionDescription, Long expectedVersion) {
        if (workbook == null) {
            clear();
            return;
        }
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to modify workbook");
        }
        synchronized (getLock(userId)) {
            Optional<WorkingSessionEntity> sessionOpt = getActiveSession(userId);
            if (sessionOpt.isEmpty()) {
                String fileName = workbook.getFileName() != null ? workbook.getFileName() : "Active Workbook";
                registerNewWorkbook(userId, workbook, fileName, 0L, actionDescription);
                return;
            }
            WorkingSessionEntity session = sessionOpt.get();
            if (expectedVersion != null && !expectedVersion.equals(session.getVersion())) {
                throw new org.springframework.dao.OptimisticLockingFailureException(
                        "Workbook was modified by another tab or operation (expected version "
                                + expectedVersion + ", current " + session.getVersion() + ")");
            }
            pushStateToSession(session, workbook, actionDescription);
        }
    }

    private void pushStateToSession(WorkingSessionEntity session, ExcelWorkbookData workbook, String actionDescription) {
        List<HistoryState> history = deserializeHistory(session.getHistoryJson());
        int currentIndex = session.getCurrentIndex() != null ? session.getCurrentIndex() : -1;

        while (history.size() > currentIndex + 1) {
            history.remove(history.size() - 1);
        }
        history.add(new HistoryState(workbook, actionDescription));
        while (history.size() > MAX_HISTORY) {
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
        session = workingSessionRepository.save(session);
        CachedWorkbook cw = new CachedWorkbook(session.getVersion(), currentIndex, workbook);
        workbookCache.put(session.getFileId(), cw);
        summaryCache.put(session.getFileId(), new CachedSummary(session.getVersion(), currentIndex, cw.sheetCount, cw.totalRows, cw.sheetNames));
    }

    @Transactional(readOnly = true)
    public ExcelWorkbookData get() {
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        Optional<WorkingSessionEntity> sessionOpt = getActiveSession(userId);
        return sessionOpt.map(this::getOrDeserializeWorkbook).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean canUndo() {
        Long userId = getCurrentUserId();
        if (userId == null) return false;
        return getActiveSession(userId)
                .map(s -> s.getCurrentIndex() != null && s.getCurrentIndex() > 0)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canRedo() {
        Long userId = getCurrentUserId();
        if (userId == null) return false;
        return getActiveSession(userId)
                .map(s -> {
                    if (s.getCurrentIndex() == null || s.getCurrentIndex() < 0) return false;
                    List<HistoryState> h = deserializeHistory(s.getHistoryJson());
                    return s.getCurrentIndex() < h.size() - 1;
                })
                .orElse(false);
    }

    @Transactional
    public HistoryState undo() {
        if (!canUndo()) return null;
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        synchronized (getLock(userId)) {
            WorkingSessionEntity session = getActiveSession(userId).orElse(null);
            if (session == null) return null;

            List<HistoryState> history = deserializeHistory(session.getHistoryJson());
            int newIndex = session.getCurrentIndex() - 1;
            if (newIndex < 0 || newIndex >= history.size()) return null;

            HistoryState targetState = history.get(newIndex);
            session.setCurrentIndex(newIndex);
            session.setWorkbookDataJson(serializeWorkbook(targetState.getWorkbook()));
            session.setLastAccessed(LocalDateTime.now());
            session = workingSessionRepository.save(session);
            CachedWorkbook cwRestored = new CachedWorkbook(session.getVersion(), newIndex, targetState.getWorkbook());
            workbookCache.put(session.getFileId(), cwRestored);
            summaryCache.put(session.getFileId(), new CachedSummary(session.getVersion(), newIndex, cwRestored.sheetCount, cwRestored.totalRows, cwRestored.sheetNames));
            return targetState;
        }
    }

    @Transactional
    public HistoryState redo() {
        if (!canRedo()) return null;
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        synchronized (getLock(userId)) {
            WorkingSessionEntity session = getActiveSession(userId).orElse(null);
            if (session == null) return null;

            List<HistoryState> history = deserializeHistory(session.getHistoryJson());
            int newIndex = session.getCurrentIndex() + 1;
            if (newIndex >= history.size()) return null;

            HistoryState targetState = history.get(newIndex);
            session.setCurrentIndex(newIndex);
            session.setWorkbookDataJson(serializeWorkbook(targetState.getWorkbook()));
            session.setLastAccessed(LocalDateTime.now());
            session = workingSessionRepository.save(session);
            CachedWorkbook cwRestored = new CachedWorkbook(session.getVersion(), newIndex, targetState.getWorkbook());
            workbookCache.put(session.getFileId(), cwRestored);
            summaryCache.put(session.getFileId(), new CachedSummary(session.getVersion(), newIndex, cwRestored.sheetCount, cwRestored.totalRows, cwRestored.sheetNames));
            return targetState;
        }
    }

    @Transactional(readOnly = true)
    public String getUndoAction() {
        if (!canUndo()) return null;
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        return getActiveSession(userId)
                .map(s -> {
                    List<HistoryState> h = deserializeHistory(s.getHistoryJson());
                    int idx = s.getCurrentIndex();
                    return (idx >= 0 && idx < h.size()) ? h.get(idx).getActionDescription() : null;
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public String getRedoAction() {
        if (!canRedo()) return null;
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        return getActiveSession(userId)
                .map(s -> {
                    List<HistoryState> h = deserializeHistory(s.getHistoryJson());
                    int idx = s.getCurrentIndex() + 1;
                    return (idx >= 0 && idx < h.size()) ? h.get(idx).getActionDescription() : null;
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public int getCurrentIndex() {
        Long userId = getCurrentUserId();
        if (userId == null) return -1;
        return getActiveSession(userId)
                .map(s -> s.getCurrentIndex() != null ? s.getCurrentIndex() : -1)
                .orElse(-1);
    }

    @Transactional(readOnly = true)
    public int getHistorySize() {
        Long userId = getCurrentUserId();
        if (userId == null) return 0;
        return getActiveSession(userId)
                .map(s -> deserializeHistory(s.getHistoryJson()).size())
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public List<String> getHistoryDescriptions() {
        return getHistoryDescriptionsForUser(getCurrentUserId());
    }

    @Transactional(readOnly = true)
    public List<String> getHistoryDescriptionsForUser(Long userId) {
        if (userId == null) return Collections.emptyList();
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
    public void clear() {
        clearForUser(getCurrentUserId());
    }

    @Transactional
    public void clearForUser(Long userId) {
        if (userId == null) return;
        synchronized (getLock(userId)) {
            Optional<WorkingSessionEntity> sessionOpt = getActiveSession(userId);
            if (sessionOpt.isPresent()) {
                WorkingSessionEntity s = sessionOpt.get();
                workbookCache.remove(s.getFileId());
                summaryCache.remove(s.getFileId());
                s.setHistoryJson("[]");
                s.setCurrentIndex(-1);
                s.setWorkbookDataJson("{}");
                s.setLastAccessed(LocalDateTime.now());
                workingSessionRepository.save(s);
            }
        }
    }

    @Transactional
    public void clearAll() {
        workingSessionRepository.deleteAll();
        workbookCache.clear();
        summaryCache.clear();
    }

    @Transactional
    public void storeForUser(Long userId, ExcelWorkbookData workbook, String actionDescription) {
        if (userId == null) {
            throw new AccessDeniedException("User ID must not be null");
        }
        synchronized (getLock(userId)) {
            if (workbook == null) {
                workingSessionRepository.deleteAllByUserId(userId);
                workbookCache.clear();
                summaryCache.clear();
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
    }

    @Transactional(readOnly = true)
    public ExcelWorkbookData getForUser(Long userId) {
        if (userId == null) return null;
        Optional<WorkingSessionEntity> active = getActiveSession(userId);
        return active.map(this::getOrDeserializeWorkbook).orElse(null);
    }

    @Scheduled(fixedRate = 1800000)
    @Transactional
    public void cleanupInactiveSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        try {
            workingSessionRepository.deleteByLastAccessedBefore(cutoff);
            workbookCache.clear();
            summaryCache.clear();
            log.debug("Expired working sessions cleaned up before {}", cutoff);
        } catch (Exception e) {
            log.error("Failed to clean up expired working sessions", e);
        }
    }
}
