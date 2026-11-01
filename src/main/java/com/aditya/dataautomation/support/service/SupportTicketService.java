package com.aditya.dataautomation.support.service;

import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.exception.InvalidRequestException;
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

    private String generateTicketCode() {
        int codeNum = 100000 + random.nextInt(900000);
        return "TBF-" + codeNum;
    }
}
