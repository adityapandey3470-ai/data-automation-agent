package com.aditya.dataautomation.support;

import com.aditya.dataautomation.auth.dto.AuthResponse;
import com.aditya.dataautomation.auth.dto.SignupRequest;
import com.aditya.dataautomation.auth.service.AuthService;
import com.aditya.dataautomation.support.entity.SupportTicket;
import com.aditya.dataautomation.support.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SupportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private SupportTicketRepository ticketRepository;

    private String userToken;
    private Long userId;
    private String userEmail;

    @BeforeEach
    void setUp() {
        userEmail = "support_tester_" + System.currentTimeMillis() + "@tabufinery.io";
        AuthResponse res = authService.signup(new SignupRequest(userEmail, "password123", "Support Tester"));
        userToken = res.getToken();
        userId = res.getUser().getId();
    }

    @Test
    @DisplayName("Security: Unauthenticated support requests are rejected with 401")
    void testUnauthenticatedRejection() throws Exception {
        mockMvc.perform(post("/api/v1/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How do I upload?\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/support/ticket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"BUG\",\"subject\":\"Help\",\"message\":\"Something failed\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Support Chat: Authenticated user receives valid advice with follow-up suggestions")
    void testSupportChatFlow() throws Exception {
        // 1. Upload inquiry
        mockMvc.perform(post("/api/v1/support/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How do I upload an Excel file?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isString())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString(".xlsx")))
                .andExpect(jsonPath("$.suggestedFollowUps").isArray())
                .andExpect(jsonPath("$.engine").isString());

        // 2. Undo/Redo inquiry
        mockMvc.perform(post("/api/v1/support/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How does Undo and Redo work?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Undo")));
    }

    @Test
    @DisplayName("Support Chat: Validation rejects empty messages")
    void testMessageValidation() throws Exception {
        mockMvc.perform(post("/api/v1/support/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Support Ticket: Submitting an issue saves a ticket with unique TBF code")
    void testSubmitTicket() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/support/ticket")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"BUG\",\"subject\":\"Formula Parsing Glitch\",\"message\":\"When applying title case on column 4, accented characters were not capitalized properly.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value(org.hamcrest.Matchers.startsWith("TBF-")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.message").isString())
                .andReturn();

        String ticketCode = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.ticketCode");
        assertThat(ticketCode).isNotBlank();

        // Verify ticket in database
        List<SupportTicket> userTickets = ticketRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(userTickets).isNotEmpty();
        assertThat(userTickets.get(0).getTicketCode()).isEqualTo(ticketCode);
        assertThat(userTickets.get(0).getUserEmail()).isEqualTo(userEmail);
        assertThat(userTickets.get(0).getCategory()).isEqualTo("BUG");
    }

    @Test
    @DisplayName("Quick Questions: Returns curated starter suggestions")
    void testGetQuickQuestions() throws Exception {
        mockMvc.perform(get("/api/v1/support/quick-questions")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").isString());
    }
}
