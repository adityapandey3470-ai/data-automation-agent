package com.aditya.dataautomation.config;

import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import com.aditya.dataautomation.dashboard.repository.DashboardRepository;
import com.aditya.dataautomation.dashboard.repository.DashboardCollaboratorRepository;
import org.springframework.security.core.Authentication;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DashboardRepository dashboardRepository;
    private final DashboardCollaboratorRepository dashboardCollaboratorRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor);
            if (token == null || !jwtService.validateToken(token)) {
                log.warn("STOMP CONNECT rejected: missing or invalid JWT token");
                throw new MessageDeliveryException("Unauthorized: missing or invalid JWT token");
            }

            Long userId = jwtService.extractUserId(token);
            if (userId == null) {
                log.warn("STOMP CONNECT rejected: missing userId in JWT");
                throw new MessageDeliveryException("Unauthorized: invalid token subject");
            }

            User user = userRepository.findById(userId).orElse(null);
            Long issuedAt = jwtService.extractIssuedAt(token);
            long validAfter = (user != null && user.getTokensValidAfter() != null) ? user.getTokensValidAfter() : 0L;
            if (user == null || !user.isEnabled() || issuedAt == null || issuedAt <= validAfter) {
                log.warn("STOMP CONNECT rejected: user not found, disabled or token revoked");
                throw new MessageDeliveryException("Unauthorized: session expired or user disabled");
            }

            UserPrincipal principal = UserPrincipal.create(user);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            accessor.setUser(authentication);
            log.debug("STOMP CONNECT authenticated for user id={}", user.getId());
        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        } else if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
            throw new MessageDeliveryException("Clients may not send messages");
        }
        return message;
    }

    /**
     * Only the owner / an accepted collaborator may subscribe to /topic/dashboard/{id}.
     * Everything else under /topic or /queue is denied; per-user destinations (/user/...) are routed by Spring.
     */
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessageDeliveryException("Subscription destination is required");
        }
        if (destination.startsWith("/user/")) {
            return;
        }
        if (!destination.startsWith("/topic/dashboard/")) {
            throw new MessageDeliveryException("Forbidden destination");
        }
        if (!(accessor.getUser() instanceof Authentication auth) || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new MessageDeliveryException("Unauthorized");
        }
        String dashboardId = destination.substring("/topic/dashboard/".length());
        Long userId = principal.getId();
        User current = userRepository.findById(userId).orElse(null);
        if (current == null || !current.isEnabled()) {
            throw new MessageDeliveryException("Unauthorized: user disabled");
        }
        boolean allowed = dashboardRepository.existsByDashboardIdAndUserId(dashboardId, userId)
                || dashboardCollaboratorRepository.existsByDashboardIdAndSharedWithUserId(dashboardId, userId);
        if (!allowed) {
            log.warn("STOMP SUBSCRIBE denied: user id={} dashboard={}", userId, dashboardId);
            throw new MessageDeliveryException("Forbidden");
        }
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            return tokenHeader.trim();
        }
        String passcode = accessor.getPasscode();
        if (passcode != null && !passcode.isBlank()) {
            return passcode.trim();
        }
        return null;
    }
}
