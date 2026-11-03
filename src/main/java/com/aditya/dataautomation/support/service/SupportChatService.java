package com.aditya.dataautomation.support.service;

import com.aditya.dataautomation.admin.service.AiUsageService;
import com.aditya.dataautomation.ai.service.GeminiClientService;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.exception.InvalidRequestException;
import com.aditya.dataautomation.support.dto.ChatMessage;
import com.aditya.dataautomation.support.dto.SupportChatRequest;
import com.aditya.dataautomation.support.dto.SupportChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportChatService {

    private final GeminiClientService geminiClient;
    private final SupportKnowledgeBase knowledgeBase;
    private final AiUsageService aiUsageService;

    private static final int MAX_REQUESTS_PER_MINUTE = 20;
    private final ConcurrentHashMap<Long, List<Long>> rateLimits = new ConcurrentHashMap<>();

    private static final String SUPPORT_SYSTEM_PROMPT = """
            You are the official AI Support Chatbot for Tabufinery SaaS.
            Your role is to guide users, troubleshoot issues, and provide clear step-by-step assistance for the Tabufinery platform.

            RULES & CONSTRAINTS:
            1. GROUNDING: Answer strictly based on Tabufinery's actual verified features provided below. NEVER invent capabilities or claim features exist if they are not documented.
            2. DISTINCTION: You are the technical support chatbot, NOT the in-sheet AI Data Analyst. For analytical questions about workbook data rows, guide the user to the "AI Analyst & Insights" page.
            3. PRIVACY: NEVER expose another user's workbook, private datasets, user accounts, password hashes, or admin credentials.
            4. ACCURACY: If an issue cannot be resolved or is a bug, advise the user to click "Submit Ticket" or email support0912@gmail.com.
            5. FORMATTING: Use clean GitHub-flavored markdown with clear bullet points and bold highlights for UI actions. Keep answers concise, actionable, and friendly.

            OFFICIAL KNOWLEDGE BASE:
            """ + SupportKnowledgeBase.APP_DOCUMENTATION;

    public SupportChatResponse processMessage(SupportChatRequest request, UserPrincipal currentUser) {
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new InvalidRequestException("Message cannot be empty.");
        }

        String userMessage = request.getMessage().trim();
        if (userMessage.length() > 1000) {
            throw new InvalidRequestException("Message exceeds maximum length of 1000 characters.");
        }

        Long userId = currentUser != null && currentUser.getId() != null ? currentUser.getId() : 0L;
        String userEmail = currentUser != null && currentUser.getUsername() != null ? currentUser.getUsername() : "anonymous";
        checkRateLimit(userId);

        long startTime = System.currentTimeMillis();

        List<Map<String, String>> historySnippet = new ArrayList<>();
        if (request.getHistory() != null) {
            List<ChatMessage> history = request.getHistory();
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                ChatMessage m = history.get(i);
                if (m.getText() != null && !m.getText().isBlank()) {
                    historySnippet.add(Map.of(
                            "role", m.getRole() != null ? m.getRole() : "user",
                            "text", m.getText()
                    ));
                }
            }
        }

        if (geminiClient.isAvailable()) {
            try {
                String aiReply = geminiClient.askSupport(userMessage, SUPPORT_SYSTEM_PROMPT, historySnippet);
                if (aiReply != null && !aiReply.isBlank()) {
                    long latency = System.currentTimeMillis() - startTime;
                    int promptTokens = Math.max(60, (userMessage.length() + 600) / 4);
                    int completionTokens = Math.max(40, aiReply.length() / 4);
                    aiUsageService.recordUsage(userId > 0 ? userId : null, userEmail, "SUPPORT_CHAT", geminiClient.getModelName(), promptTokens, completionTokens, latency, "SUCCESS", userMessage);

                    List<String> followUps = generateFollowUps(userMessage);
                    return SupportChatResponse.builder()
                            .reply(aiReply.trim())
                            .suggestedFollowUps(followUps)
                            .canEscalateToTicket(true)
                            .engine("GEMINI_AI")
                            .build();
                }
            } catch (Exception e) {
                log.warn("Gemini support chatbot call failed: {}. Falling back to knowledge base.", e.getMessage());
            }
        }

        long latency = System.currentTimeMillis() - startTime;
        aiUsageService.recordUsage(userId > 0 ? userId : null, userEmail, "SUPPORT_CHAT", "KNOWLEDGE_BASE", 0, 0, latency, "FALLBACK", userMessage);

        String deterministicReply = knowledgeBase.findDeterministicAnswer(userMessage);
        List<String> followUps = generateFollowUps(userMessage);

        return SupportChatResponse.builder()
                .reply(deterministicReply)
                .suggestedFollowUps(followUps)
                .canEscalateToTicket(true)
                .engine("KNOWLEDGE_BASE")
                .build();
    }

    private void checkRateLimit(Long userId) {
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60_000L;

        rateLimits.compute(userId, (k, timestamps) -> {
            if (timestamps == null) {
                timestamps = new ArrayList<>();
            }
            timestamps.removeIf(t -> t < oneMinuteAgo);
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                throw new InvalidRequestException("Support chat rate limit exceeded. Please wait a moment before sending another message.");
            }
            timestamps.add(now);
            return timestamps;
        });
    }

    private List<String> generateFollowUps(String userMessage) {
        String lower = userMessage.toLowerCase(Locale.ROOT);
        if (lower.contains("upload") || lower.contains("file")) {
            return List.of("How do I clean missing data?", "Can I upload multiple sheets?", "How do I save my project?");
        }
        if (lower.contains("clean") || lower.contains("duplicate") || lower.contains("missing")) {
            return List.of("How does Undo/Redo work?", "How do I preview changes before applying?", "How to export the cleaned file?");
        }
        if (lower.contains("undo") || lower.contains("redo") || lower.contains("preview")) {
            return List.of("What is the history limit for Undo?", "How to export a PDF report?", "How to save changes to project?");
        }
        if (lower.contains("export") || lower.contains("pdf")) {
            return List.of("Can I export CSV as well?", "How does executive PDF report work?", "How to clean data first?");
        }
        if (lower.contains("error") || lower.contains("issue") || lower.contains("fail")) {
            return List.of("Submit a support ticket", "Check supported Excel formats", "Contact support email");
        }
        return List.of(
                "How do I upload an Excel file?",
                "How does Undo/Redo work?",
                "How can I export PDF reports?"
        );
    }
}
