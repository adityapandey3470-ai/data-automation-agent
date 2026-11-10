package com.aditya.dataautomation.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import com.aditya.dataautomation.auth.service.JwtService;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;

    @Value("${app.security.behind-proxy:false}")
    private boolean behindProxy;

    @Value("${app.security.trusted-proxies:127.0.0.1,::1}")
    private String trustedProxies;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        if (request.getPathInfo() != null) {
            path = path + request.getPathInfo();
        }
        String method = request.getMethod();
        String clientIp = extractClientIp(request);
        request.setAttribute("clientIp", clientIp);

        RateLimiterService.RateLimitResult res;

        if (path.startsWith("/api/")) {
            res = rateLimiterService.tryAcquireWithResult("api:" + clientIp, 600, 60);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Request rate limit exceeded. Please wait a moment.", res.retryAfterSeconds());
                return;
            }
        }

        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/auth/login")) {
            res = rateLimiterService.tryAcquireWithResult("login:" + clientIp, 15, 60);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Too many login attempts. Please wait a moment before trying again.", res.retryAfterSeconds());
                return;
            }
        }

        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/auth/signup")) {
            res = rateLimiterService.tryAcquireWithResult("signup:" + clientIp, 10, 300);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Too many signup requests. Please wait before creating more accounts.", res.retryAfterSeconds());
                return;
            }
        }

        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/auth/forgot-password")) {
            res = rateLimiterService.tryAcquireWithResult("forgot:" + clientIp, 5, 300);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Password reset request limit reached. Please check your email or wait a moment.", res.retryAfterSeconds());
                return;
            }
        }

        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/auth/reset-password")) {
            res = rateLimiterService.tryAcquireWithResult("reset:" + clientIp, 10, 300);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Too many password reset attempts. Please request a new link.", res.retryAfterSeconds());
                return;
            }
        }

        if (path.startsWith("/api/v1/ai/")) {
            res = rateLimiterService.tryAcquireWithResult("ai:" + clientIp, 30, 60);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "AI service rate limit exceeded. Please wait a moment before sending another query.", res.retryAfterSeconds());
                return;
            }
        }

        if ("POST".equalsIgnoreCase(method) && (path.startsWith("/api/v1/files/") || path.startsWith("/api/v1/jobs/"))) {
            res = rateLimiterService.tryAcquireWithResult("upload:" + clientIp, 20, 60);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "File upload rate limit reached. Please wait a moment before uploading again.", res.retryAfterSeconds());
                return;
            }
        }

        if (path.startsWith("/api/v1/export/")) {
            res = rateLimiterService.tryAcquireWithResult("export:" + clientIp, 25, 60);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Export request rate limit reached. Please wait a moment.", res.retryAfterSeconds());
                return;
            }
        }

        if (path.startsWith("/api/v1/support/bug-report")) {
            res = rateLimiterService.tryAcquireWithResult("bug-report:" + clientIp, 5, 300);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Bug report submission limit reached. Please wait a few minutes.", res.retryAfterSeconds());
                return;
            }
        }

        // ---- additional per-IP limits for endpoints that were previously only covered by the generic limit ----
        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/auth/refresh")) {
            res = rateLimiterService.tryAcquireWithResult("refresh:" + clientIp, 30, 60);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Too many session refresh requests. Please wait a moment.", res.retryAfterSeconds());
                return;
            }
        }

        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/auth/resend-verification")) {
            res = rateLimiterService.tryAcquireWithResult("resend:" + clientIp, 5, 300);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Too many verification email requests. Please wait a few minutes.", res.retryAfterSeconds());
                return;
            }
        }

        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/auth/verify-email")) {
            res = rateLimiterService.tryAcquireWithResult("verify:" + clientIp, 20, 300);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Too many verification attempts. Please wait a few minutes.", res.retryAfterSeconds());
                return;
            }
        }

        if (path.startsWith("/ws")) {
            res = rateLimiterService.tryAcquireWithResult("ws:" + clientIp, 60, 60);
            if (!res.allowed()) {
                rejectRateLimit(response, path, "Too many connection attempts. Please wait a moment.", res.retryAfterSeconds());
                return;
            }
        }

        // ---- per-USER limits (an attacker rotating IPs, or many users behind one NAT, are handled separately) ----
        if (path.startsWith("/api/") && !"GET".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method)) {
            Long userId = resolveUserId(request);
            if (userId != null) {
                String bucket = null;
                int limit = 0;
                int window = 60;
                String msg = "Too many requests. Please wait a moment.";

                if (path.startsWith("/api/v1/ai/")) {
                    bucket = "ai-user"; limit = 30; window = 60;
                    msg = "AI service rate limit exceeded for your account. Please wait a moment.";
                } else if (path.startsWith("/api/v1/jobs/")) {
                    bucket = "jobs-user"; limit = 10; window = 60;
                    msg = "Too many background jobs started. Please wait a moment.";
                } else if (path.startsWith("/api/v1/files/")) {
                    bucket = "upload-user"; limit = 20; window = 60;
                    msg = "File upload rate limit reached for your account.";
                } else if (path.startsWith("/api/v1/support/ticket")) {
                    bucket = "ticket-user"; limit = 5; window = 600;
                    msg = "Support ticket limit reached. Please wait before creating another ticket.";
                } else if (path.startsWith("/api/v1/support/chat")) {
                    bucket = "chat-user"; limit = 20; window = 60;
                    msg = "Support chat rate limit reached. Please wait a moment.";
                } else if (path.startsWith("/api/v1/projects/save")) {
                    bucket = "save-user"; limit = 30; window = 60;
                    msg = "Too many save requests. Please wait a moment.";
                } else if (path.startsWith("/api/v1/dashboard/collaboration/share")) {
                    bucket = "share-user"; limit = 20; window = 600;
                    msg = "Too many dashboard invitations. Please wait before inviting more people.";
                }

                if (bucket != null) {
                    res = rateLimiterService.tryAcquireWithResult(bucket + ":" + userId, limit, window);
                    if (!res.allowed()) {
                        rejectRateLimit(response, path, msg, res.retryAfterSeconds());
                        return;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Best-effort user id from a valid Bearer token (signature + expiry checked). Returns null when absent/invalid. */
    private Long resolveUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtService.extractUserId(header.substring(7));
        } catch (Exception e) {
            return null;
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";

        if (!behindProxy) {
            return remoteAddr;
        }

        if (isTrustedProxy(remoteAddr)) {
            String xf = request.getHeader("X-Forwarded-For");
            if (xf != null && !xf.isBlank()) {
                String[] parts = xf.split(",");
                for (int i = parts.length - 1; i >= 0; i--) {
                    String candidate = parts[i].trim();
                    if (!candidate.isBlank() && !isTrustedProxy(candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return remoteAddr;
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank() || trustedProxies == null || trustedProxies.isBlank()) {
            return false;
        }
        String[] trustedList = trustedProxies.split(",");
        for (String trusted : trustedList) {
            if (trusted.trim().equalsIgnoreCase(ip.trim())) {
                return true;
            }
        }
        return false;
    }

    private void rejectRateLimit(HttpServletResponse response, String path, String message, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        Map<String, Object> body = Map.of(
                "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                "error", "Too Many Requests",
                "message", message,
                "retryAfterSeconds", retryAfterSeconds,
                "path", path,
                "timestamp", LocalDateTime.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
