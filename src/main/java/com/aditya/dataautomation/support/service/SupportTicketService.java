package com.aditya.dataautomation.support.service;

import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.exception.InvalidRequestException;
import com.aditya.dataautomation.support.dto.CreateBugReportRequest;
import com.aditya.dataautomation.support.dto.CreateTicketRequest;
import com.aditya.dataautomation.support.dto.TicketResponse;
import com.aditya.dataautomation.support.entity.SupportTicket;
import com.aditya.dataautomation.support.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new InvalidRequestException("You must be signed in to submit a support ticket.");
        }

        if (request == null || request.getSubject() == null || request.getSubject().isBlank()) {
            throw new InvalidRequestException("Ticket subject is required.");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new InvalidRequestException("Ticket message details are required.");
        }

        String ticketCode = generateTicketCode();

        SupportTicket ticket = SupportTicket.builder()
                .ticketCode(ticketCode)
                .userId(currentUser.getId())
                .userEmail(currentUser.getEmail())
                .userName(currentUser.getName())
                .category(request.getCategory() != null && !request.getCategory().isBlank()
                        ? request.getCategory().trim().toUpperCase()
                        : "OTHER")
                .subject(request.getSubject().trim())
                .message(request.getMessage().trim())
                .status("OPEN")
                .build();

        ticket = ticketRepository.save(ticket);
        log.info("Support ticket created [{}] by user {} ({}) - Subject: {}",
                ticketCode, currentUser.getId(), currentUser.getEmail(), ticket.getSubject());

        return TicketResponse.builder()
                .ticketCode(ticketCode)
                .status(ticket.getStatus())
                .message("Your support ticket has been submitted successfully. Our engineering team has been notified and will follow up via your account email (" + currentUser.getEmail() + ").")
                .createdAt(ticket.getCreatedAt() != null ? ticket.getCreatedAt() : LocalDateTime.now())
                .build();
    }

    @Transactional
    public TicketResponse createBugReport(CreateBugReportRequest request, UserPrincipal currentUser) {
        if (request == null || request.getDescription() == null || request.getDescription().trim().length() < 10) {
            throw new InvalidRequestException("Bug description is required (at least 10 characters describing expected vs actual behaviour).");
        }
        if (request.getStepsToReproduce() == null || request.getStepsToReproduce().trim().length() < 5) {
            throw new InvalidRequestException("Steps to reproduce are required (at least 5 characters).");
        }

        String ticketCode = generateUniqueTicketCode("BUG-");
        String severity = (request.getSeverity() != null && !request.getSeverity().isBlank())
                ? request.getSeverity().trim().toUpperCase()
                : "MEDIUM";

        Long userId = (currentUser != null) ? currentUser.getId() : 0L;
        String userEmail = (currentUser != null) ? currentUser.getEmail()
                : (request.getUserEmail() != null && !request.getUserEmail().isBlank() ? request.getUserEmail().replaceAll("[\\r\\n]", "").trim() : "anonymous@tabufinery.io");
        String userName = (currentUser != null) ? currentUser.getName() : "Guest User";

        String desc = request.getDescription().trim();
        String oneLine = desc.replaceAll("[\\r\\n]+", " ");
        String subject = "Bug: " + (oneLine.length() > 60 ? oneLine.substring(0, 57) + "..." : oneLine);

        StringBuilder msg = new StringBuilder();
        msg.append("Severity: ").append(severity).append("\n\n");
        msg.append("Description:\n").append(desc).append("\n\n");
        if (request.getStepsToReproduce() != null && !request.getStepsToReproduce().isBlank()) {
            msg.append("Steps to Reproduce:\n").append(request.getStepsToReproduce().trim());
        }

        SupportTicket ticket = SupportTicket.builder()
                .ticketCode(ticketCode)
                .userId(userId)
                .userEmail(userEmail)
                .userName(userName)
                .category("BUG")
                .subject(subject)
                .message(msg.toString())
                .status("OPEN")
                .build();

        ticket = ticketRepository.save(ticket);

        log.warn("🚨 [BUG REPORT RECEIVED] Code: {}, Severity: {}, Reported By: {} ({}) | Subject: {}\nSteps: {}",
                ticketCode, severity, userName, userEmail, subject, request.getStepsToReproduce());

        return TicketResponse.builder()
                .ticketCode(ticketCode)
                .status(ticket.getStatus())
                .message("Bug report submitted successfully! Reference code: " + ticketCode + ". Our engineering team has been notified.")
                .createdAt(ticket.getCreatedAt() != null ? ticket.getCreatedAt() : LocalDateTime.now())
                .build();
    }

    private String generateTicketCode() {
        return generateUniqueTicketCode("TBF-");
    }

    private String generateUniqueTicketCode(String prefix) {
        for (int i = 0; i < 10; i++) {
            long ts = System.currentTimeMillis() % 1000000;
            int rand = 1000 + random.nextInt(9000);
            String candidate = prefix + ts + "-" + rand;
            if (!ticketRepository.existsByTicketCode(candidate)) {
                return candidate;
            }
        }
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
