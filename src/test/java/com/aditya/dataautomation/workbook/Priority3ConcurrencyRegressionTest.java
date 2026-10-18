package com.aditya.dataautomation.workbook;

import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.auth.security.RateLimiterService;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.workbook.entity.WorkingSessionEntity;
import com.aditya.dataautomation.workbook.repository.WorkingSessionRepository;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class Priority3ConcurrencyRegressionTest {

    @Autowired
    private WorkbookSessionHolder workbookSessionHolder;

    @Autowired
    private WorkingSessionRepository workingSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RateLimiterService rateLimiterService;

    private Long userAId;
    private Long userBId;

    @BeforeEach
    void setUp() {
        workingSessionRepository.deleteAll();
        rateLimiterService.reset();

        userAId = userRepository.findByEmailIgnoreCase("user_concurrency_a@test.com")
                .map(User::getId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("user_concurrency_a@test.com")
                        .name("User A")
                        .passwordHash("$2a$10$none")
                        .role("ROLE_USER")
                        .enabled(true)
                        .build()).getId());

        userBId = userRepository.findByEmailIgnoreCase("user_concurrency_b@test.com")
                .map(User::getId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("user_concurrency_b@test.com")
                        .name("User B")
                        .passwordHash("$2a$10$none")
                        .role("ROLE_USER")
                        .enabled(true)
                        .build()).getId());
    }

    private ExcelWorkbookData createMockWorkbook(String sheetName, int rows) {
        List<Map<String, Object>> rowList = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ID", i + 1);
            r.put("Val", "Data " + i);
            rowList.add(r);
        }
        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName(sheetName)
                .headers(List.of("ID", "Val"))
                .rows(rowList)
                .build();
        return ExcelWorkbookData.builder()
                .fileName("test.xlsx")
                .sheets(List.of(sheet))
                .build();
    }

    @Test
    @DisplayName("Verify history snapshots are bounded to 10 max steps to avoid memory/DB bloat")
    void testHistoryBoundedTo10Snapshots() {
        ExcelWorkbookData initialWb = createMockWorkbook("Sheet1", 5);
        workbookSessionHolder.storeForUser(userAId, initialWb, "Initial Upload");

        // Perform 15 successive operations
        for (int i = 1; i <= 15; i++) {
            ExcelWorkbookData editWb = createMockWorkbook("Sheet1", 5 + i);
            workbookSessionHolder.storeForUser(userAId, editWb, "Operation " + i);
        }

        List<String> history = workbookSessionHolder.getHistoryDescriptionsForUser(userAId);
        assertThat(history).hasSize(10);
        // The most recent operation should be the active one
        assertThat(history.get(history.size() - 1)).contains("Operation 15");
    }

    @Test
    @DisplayName("Verify Optimistic Locking (@Version) protects against dirty concurrent session overwrite")
    void testOptimisticLockingOnWorkingSession() {
        ExcelWorkbookData initialWb = createMockWorkbook("Sheet1", 10);
        String fileId = workbookSessionHolder.registerNewWorkbook(userAId, initialWb, "concurrent.xlsx", 1024L, "Initial");

        WorkingSessionEntity session1 = workingSessionRepository.findById(fileId).orElseThrow();
        WorkingSessionEntity session2 = workingSessionRepository.findById(fileId).orElseThrow();

        assertThat(session1.getVersion()).isNotNull();

        // First update succeeds
        session1.setFileName("concurrent_updated_v1.xlsx");
        workingSessionRepository.saveAndFlush(session1);

        // Stale update from second entity with older version must fail with OptimisticLockingFailureException
        session2.setFileName("concurrent_updated_v2_stale.xlsx");
        assertThatThrownBy(() -> workingSessionRepository.saveAndFlush(session2))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Verify RateLimiterService handles concurrent requests without race conditions")
    void testRateLimiterConcurrency() throws InterruptedException {
        int threads = 10;
        int requestsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Boolean> results = new ConcurrentLinkedQueue<>();

        // Rate limit: 20 requests per 10 seconds
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int r = 0; r < requestsPerThread; r++) {
                        boolean allowed = rateLimiterService.tryAcquire("concurrent_client_ip", 20, 10);
                        results.add(allowed);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(finished).isTrue();

        long allowedCount = results.stream().filter(b -> b).count();
        long rejectedCount = results.stream().filter(b -> !b).count();

        // Exactly 20 permitted, remaining 30 rejected
        assertThat(allowedCount).isEqualTo(20);
        assertThat(rejectedCount).isEqualTo(30);
    }
}
