package com.aditya.dataautomation.job;

import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.job.dto.AsyncJobDto;
import com.aditya.dataautomation.job.model.JobStatus;
import com.aditya.dataautomation.job.model.JobType;
import com.aditya.dataautomation.job.service.AsyncJobService;
import com.aditya.dataautomation.project.entity.Project;
import com.aditya.dataautomation.project.repository.ProjectRepository;
import com.aditya.dataautomation.support.entity.SupportTicket;
import com.aditya.dataautomation.support.repository.SupportTicketRepository;
import com.aditya.dataautomation.workbook.entity.WorkingSessionEntity;
import com.aditya.dataautomation.workbook.repository.WorkingSessionRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AsyncJobQueueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AsyncJobService asyncJobService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkingSessionRepository workingSessionRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    private byte[] createTestExcelBytes() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("SalesData");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("Item");
            header.createCell(2).setCellValue("Amount");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue("Widget Pro");
            r1.createCell(2).setCellValue(99.50);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private User getOrCreateTestUser() {
        return userRepository.findAll().stream().findFirst().orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Test User")
                        .email("testuser-" + UUID.randomUUID() + "@tabufinery.io")
                        .passwordHash("hashed-password-123")
                        .role("ROLE_USER")
                        .enabled(true)
                        .build())
        );
    }

    @Test
    @DisplayName("Should create, update, and complete an async background job")
    void testAsyncJobLifecycle() {
        User user = getOrCreateTestUser();
        AsyncJobDto job = asyncJobService.createJob(user.getId(), JobType.FILE_IMPORT, "Test 100K Rows Import");
        assertThat(job.getJobId()).isNotNull();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getProgress()).isEqualTo(0);

        asyncJobService.startJob(job.getJobId(), "Allocating worker thread");
        AsyncJobDto runningJob = asyncJobService.getJob(job.getJobId(), user.getId());
        assertThat(runningJob.getStatus()).isEqualTo(JobStatus.RUNNING);

        asyncJobService.updateProgress(job.getJobId(), 45, "Parsed 45,000 rows");
        AsyncJobDto progressJob = asyncJobService.getJob(job.getJobId(), user.getId());
        assertThat(progressJob.getProgress()).isEqualTo(45);
        assertThat(progressJob.getStatusMessage()).isEqualTo("Parsed 45,000 rows");

        asyncJobService.completeJob(job.getJobId(), "session-abc-123", "All rows imported successfully");
        AsyncJobDto completedJob = asyncJobService.getJob(job.getJobId(), user.getId());
        assertThat(completedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(completedJob.getProgress()).isEqualTo(100);
        assertThat(completedJob.getResultData()).isEqualTo("session-abc-123");
        assertThat(completedJob.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should allow cancelling a pending or running job")
    void testJobCancellation() {
        User user = getOrCreateTestUser();
        AsyncJobDto job = asyncJobService.createJob(user.getId(), JobType.BATCH_ANALYSIS, "Heavy Pivot Calculation");
        boolean cancelled = asyncJobService.cancelJob(job.getJobId(), user.getId());
        assertThat(cancelled).isTrue();

        AsyncJobDto cancelledJob = asyncJobService.getJob(job.getJobId(), user.getId());
        assertThat(cancelledJob.getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should expose REST endpoints to check job status and list recent jobs")
    void testAsyncJobRestEndpoints() throws Exception {
        AsyncJobDto job = asyncJobService.createJob(JobType.DATA_CLEANING, "Clean sales.xlsx");

        mockMvc.perform(get("/api/v1/jobs/" + job.getJobId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(job.getJobId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.progress").value(0));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should accept valid Excel upload asynchronously and immediately return HTTP 202 with job token")
    void testAsyncExcelUploadEndpoint() throws Exception {
        byte[] excelBytes = createTestExcelBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales-valid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/v1/jobs/excel-async").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.isOneOf("PENDING", "RUNNING")));
    }

    @Test
    @DisplayName("Verify optimistic locking @Version column on User entity")
    void testOptimisticLockingUser() {
        User user = User.builder()
                .name("Optimistic User")
                .email("optimistic-" + UUID.randomUUID() + "@tabufinery.io")
                .passwordHash("hashed-pw-123")
                .role("ROLE_USER")
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        assertThat(saved.getVersion()).isNotNull();
        assertThat(saved.getVersion()).isGreaterThanOrEqualTo(0L);

        saved.setName("Updated Optimistic Name");
        User updated = userRepository.save(saved);
        assertThat(updated.getVersion()).isGreaterThanOrEqualTo(saved.getVersion());
    }

    @Test
    @DisplayName("Verify optimistic locking @Version column on WorkingSessionEntity")
    void testOptimisticLockingWorkingSession() {
        User owner = getOrCreateTestUser();

        WorkingSessionEntity session = WorkingSessionEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(owner.getId())
                .fileId("file-" + UUID.randomUUID())
                .fileName("test.xlsx")
                .workbookDataJson("{}")
                .lastAccessed(LocalDateTime.now())
                .build();

        WorkingSessionEntity saved = workingSessionRepository.save(session);
        assertThat(saved.getVersion()).isNotNull();
    }

    @Test
    @DisplayName("Verify optimistic locking @Version column on Project entity")
    void testOptimisticLockingProject() {
        User owner = getOrCreateTestUser();

        Project project = Project.builder()
                .id(UUID.randomUUID().toString())
                .name("Project Alpha")
                .fileName("alpha.xlsx")
                .workbookDataJson("{}")
                .user(owner)
                .build();

        Project saved = projectRepository.save(project);
        assertThat(saved.getVersion()).isNotNull();
    }

    @Test
    @DisplayName("Verify optimistic locking @Version column on SupportTicket entity")
    void testOptimisticLockingSupportTicket() {
        User owner = getOrCreateTestUser();

        SupportTicket ticket = SupportTicket.builder()
                .ticketCode("TCK-" + UUID.randomUUID().toString().substring(0, 8))
                .userId(owner.getId())
                .userEmail(owner.getEmail())
                .userName(owner.getName())
                .category("BUG")
                .subject("Test Bug")
                .message("Test message")
                .status("OPEN")
                .build();

        SupportTicket saved = supportTicketRepository.save(ticket);
        assertThat(saved.getVersion()).isNotNull();
    }
}
