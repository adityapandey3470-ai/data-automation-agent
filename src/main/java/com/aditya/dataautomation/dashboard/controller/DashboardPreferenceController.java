package com.aditya.dataautomation.dashboard.controller;

import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.dashboard.service.DashboardPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping({"/api/v1/dashboard/preferences", "/api/dashboard/preferences"})
@RequiredArgsConstructor
@Tag(name = "Dashboard Preferences", description = "Get, save, and reset user-specific Overview Dashboard visual customization preferences")
public class DashboardPreferenceController {

    private final DashboardPreferenceService preferenceService;
    private final UserRepository userRepository;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required to access dashboard preferences");
        }
        if (auth.getPrincipal() instanceof UserPrincipal p) {
            return p.getId();
        }
        if (auth.getPrincipal() instanceof UserDetails ud) {
            return userRepository.findByEmailIgnoreCase(ud.getUsername())
                    .map(User::getId)
                    .orElseThrow(() -> new AccessDeniedException("User not found: " + ud.getUsername()));
        }
        throw new AccessDeniedException("Valid user identity required");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get authenticated user's dashboard customization preferences")
    public ResponseEntity<String> getPreferences() {
        Long userId = getCurrentUserId();
        String json = preferenceService.getPreferences(userId);
        return ResponseEntity.ok(json);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update authenticated user's dashboard customization preferences")
    public ResponseEntity<String> savePreferences(@RequestBody String preferencesJson) {
        Long userId = getCurrentUserId();
        String saved = preferenceService.savePreferences(userId, preferencesJson);
        return ResponseEntity.ok(saved);
    }

    @PostMapping(value = "/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reset authenticated user's dashboard customization preferences to defaults")
    public ResponseEntity<String> resetPreferencesPost() {
        Long userId = getCurrentUserId();
        String reset = preferenceService.resetPreferences(userId);
        return ResponseEntity.ok(reset);
    }

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete and reset dashboard customization preferences to defaults")
    public ResponseEntity<String> resetPreferencesDelete() {
        Long userId = getCurrentUserId();
        String reset = preferenceService.resetPreferences(userId);
        return ResponseEntity.ok(reset);
    }
}
