package com.aditya.dataautomation.dashboard.service.impl;

import com.aditya.dataautomation.dashboard.entity.UserDashboardPreferenceEntity;
import com.aditya.dataautomation.dashboard.repository.UserDashboardPreferenceRepository;
import com.aditya.dataautomation.dashboard.service.DashboardPreferenceService;
import com.aditya.dataautomation.exception.InvalidRequestException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardPreferenceServiceImpl implements DashboardPreferenceService {

    private final UserDashboardPreferenceRepository repository;
    private final ObjectMapper objectMapper;

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$");

    private static final Set<String> ALLOWED_STYLES = Set.of(
            "matte", "modern", "glass", "minimal", "dark", "bento", "neumorphic"
    );

    private static final Set<String> ALLOWED_CARD_STYLES = Set.of(
            "flat", "bordered", "soft", "glass", "elevated"
    );

    private static final Set<String> ALLOWED_RADII = Set.of(
            "sharp", "medium", "rounded"
    );

    private static final Set<String> ALLOWED_SHADOWS = Set.of(
            "none", "subtle", "strong"
    );

    private static final Set<String> ALLOWED_DENSITIES = Set.of(
            "compact", "comfortable", "spacious"
    );

    private static final Set<String> ALLOWED_TYPOGRAPHY = Set.of(
            "small", "medium", "large"
    );

    private static final String DEFAULT_PREFERENCES = """
            {
              "style": "modern",
              "palette": "purple_blue",
              "useCustomColors": false,
              "customColors": {
                "primary": "#4f46e5",
                "secondary": "#2563eb",
                "bg": "#f8f9fa",
                "surface": "#ffffff",
                "cardBg": "#ffffff",
                "border": "#e2e8f0",
                "textPrimary": "#0f172a",
                "textSecondary": "#475569"
              },
              "background": {
                "type": "solid",
                "gradientPreset": "aurora",
                "pattern": "none"
              },
              "card": {
                "style": "bordered",
                "radius": "medium",
                "shadow": "subtle"
              },
              "layout": {
                "density": "comfortable",
                "widgets": [
                  {"id": "stat_cards", "label": "Key Performance Metrics", "visible": true, "order": 0},
                  {"id": "sheet_structure", "label": "Workbook Structure & Sheets", "visible": true, "order": 1},
                  {"id": "overview_charts", "label": "Executive Overview Analytics", "visible": true, "order": 2},
                  {"id": "quick_actions", "label": "Engine Quick Actions", "visible": true, "order": 3}
                ]
              },
              "typography": {
                "size": "medium",
                "emphasis": "standard",
                "reducedMotion": false
              }
            }
            """.stripIndent();

    @Override
    @Transactional(readOnly = true)
    public String getPreferences(Long userId) {
        if (userId == null) {
            return DEFAULT_PREFERENCES;
        }

        return repository.findByUserId(userId)
                .map(UserDashboardPreferenceEntity::getPreferenceJson)
                .filter(json -> json != null && !json.isBlank())
                .orElse(DEFAULT_PREFERENCES);
    }

    @Override
    @Transactional
    public String savePreferences(Long userId, String preferencesJson) {
        if (userId == null) {
            throw new InvalidRequestException("Authenticated user identity required to persist preferences.");
        }

        if (preferencesJson == null || preferencesJson.isBlank()) {
            throw new InvalidRequestException("Preferences payload cannot be empty.");
        }

        String sanitizedJson = validateAndSanitize(preferencesJson);

        UserDashboardPreferenceEntity entity = repository.findByUserId(userId)
                .orElseGet(() -> UserDashboardPreferenceEntity.builder()
                        .userId(userId)
                        .build());

        entity.setPreferenceJson(sanitizedJson);
        repository.save(entity);

        return sanitizedJson;
    }

    @Override
    @Transactional
    public String resetPreferences(Long userId) {
        if (userId != null) {
            repository.deleteByUserId(userId);
        }
        return DEFAULT_PREFERENCES;
    }

    @SuppressWarnings("unchecked")
    private String validateAndSanitize(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            String style = (String) map.getOrDefault("style", "modern");
            if (!ALLOWED_STYLES.contains(style)) {
                map.put("style", "modern");
            }

            if (!map.containsKey("palette") || !(map.get("palette") instanceof String)) {
                map.put("palette", "purple_blue");
            }

            if (map.containsKey("customColors") && map.get("customColors") instanceof Map<?, ?> customMap) {
                Map<String, Object> sanitizedCustom = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : customMap.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    String val = String.valueOf(entry.getValue());
                    if (HEX_COLOR_PATTERN.matcher(val).matches()) {
                        sanitizedCustom.put(key, val);
                    }
                }
                map.put("customColors", sanitizedCustom);
            }

            if (map.containsKey("card") && map.get("card") instanceof Map<?, ?> cardMap) {
                Map<String, Object> sanitizedCard = new LinkedHashMap<>((Map<String, Object>) cardMap);
                String cardStyle = (String) sanitizedCard.get("style");
                if (cardStyle == null || !ALLOWED_CARD_STYLES.contains(cardStyle)) {
                    sanitizedCard.put("style", "bordered");
                }
                String radius = (String) sanitizedCard.get("radius");
                if (radius == null || !ALLOWED_RADII.contains(radius)) {
                    sanitizedCard.put("radius", "medium");
                }
                String shadow = (String) sanitizedCard.get("shadow");
                if (shadow == null || !ALLOWED_SHADOWS.contains(shadow)) {
                    sanitizedCard.put("shadow", "subtle");
                }
                map.put("card", sanitizedCard);
            }

            if (map.containsKey("layout") && map.get("layout") instanceof Map<?, ?> layoutMap) {
                Map<String, Object> sanitizedLayout = new LinkedHashMap<>((Map<String, Object>) layoutMap);
                String density = (String) sanitizedLayout.get("density");
                if (density == null || !ALLOWED_DENSITIES.contains(density)) {
                    sanitizedLayout.put("density", "comfortable");
                }
                map.put("layout", sanitizedLayout);
            }

            if (map.containsKey("typography") && map.get("typography") instanceof Map<?, ?> typoMap) {
                Map<String, Object> sanitizedTypo = new LinkedHashMap<>((Map<String, Object>) typoMap);
                String size = (String) sanitizedTypo.get("size");
                if (size == null || !ALLOWED_TYPOGRAPHY.contains(size)) {
                    sanitizedTypo.put("size", "medium");
                }
                map.put("typography", sanitizedTypo);
            }

            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Invalid preferences JSON received: {}. Falling back to default.", e.getMessage());
            return DEFAULT_PREFERENCES;
        }
    }
}
