package com.aditya.dataautomation.support.controller;

import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.support.dto.CreateBugReportRequest;
import com.aditya.dataautomation.support.dto.CreateTicketRequest;
import com.aditya.dataautomation.support.dto.SupportChatRequest;
import com.aditya.dataautomation.support.dto.SupportChatResponse;
import com.aditya.dataautomation.support.dto.TicketResponse;
import com.aditya.dataautomation.support.service.SupportChatService;
import com.aditya.dataautomation.support.service.SupportKnowledgeBase;
import com.aditya.dataautomation.support.service.SupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Tag(name = "AI Support & Helpdesk", description = "AI Support Chatbot and Support Ticket management")
public class SupportController {

    private final SupportChatService chatService;
    private final SupportTicketService ticketService;

    @PostMapping("/chat")
    @Operation(summary = "Chat with AI Support Bot", description = "Answers support inquiries about Tabufinery features, workflows, and troubleshooting")
    public ResponseEntity<SupportChatResponse> chat(
            @Valid @RequestBody SupportChatRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        SupportChatResponse response = chatService.processMessage(request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ticket")
    @Operation(summary = "Submit a support escalation ticket", description = "Submits an official support ticket tied to the user account")
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        TicketResponse response = ticketService.createTicket(request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bug-report")
    @Operation(summary = "Submit a bug report", description = "Submits a user bug report with description, steps to reproduce, and severity")
    public ResponseEntity<TicketResponse> createBugReport(
            @Valid @RequestBody CreateBugReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        TicketResponse response = ticketService.createBugReport(request, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/quick-questions")
    @Operation(summary = "Get curated support starter questions", description = "Returns common suggested questions to kickstart the support chat")
    public ResponseEntity<List<String>> getQuickQuestions() {
        return ResponseEntity.ok(SupportKnowledgeBase.QUICK_QUESTIONS);
    }
}
