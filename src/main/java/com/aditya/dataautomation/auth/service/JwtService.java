package com.aditya.dataautomation.auth.service;

import com.aditya.dataautomation.config.properties.JwtProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    public String generateToken(Long userId, String email, String name) {
        return generateToken(userId, email, name, "ROLE_USER");
    }

    public String generateToken(Long userId, String email, String name, String role) {
        try {
            long now = Instant.now().getEpochSecond();
            long exp = Instant.now().plus(jwtProperties.getAccessTokenMinutes(), ChronoUnit.MINUTES).getEpochSecond();

            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                    "sub", String.valueOf(userId),
                    "email", email,
                    "name", name,
                    "role", role != null ? role : "ROLE_USER",
                    "iat", now,
                    "exp", exp
            ));

            String headerEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = sign(headerEncoded + "." + payloadEncoded, jwtProperties.getSecret());

            return headerEncoded + "." + payloadEncoded + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            if (token == null) {
                return false;
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;

            String expectedSig = sign(parts[0] + "." + parts[1], jwtProperties.getSecret());
            if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8), expectedSig.getBytes(StandardCharsets.UTF_8))) {
                return false;
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<?, ?> claims = objectMapper.readValue(payloadBytes, Map.class);
            long exp = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<?, ?> extractAllClaimsIfValid(String token) {
        if (token == null || !validateToken(token)) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(payloadBytes, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    public Long extractUserId(String token) {
        Map<?, ?> claims = extractAllClaimsIfValid(token);
        if (claims == null) return null;
        try {
            Object sub = claims.get("sub");
            return sub != null ? Long.parseLong(String.valueOf(sub)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public Long extractIssuedAt(String token) {
        Map<?, ?> claims = extractAllClaimsIfValid(token);
        if (claims == null) return null;
        Object iat = claims.get("iat");
        return iat instanceof Number n ? n.longValue() : null;
    }

    public String extractEmail(String token) {
        Map<?, ?> claims = extractAllClaimsIfValid(token);
        if (claims == null) return null;
        try {
            return (String) claims.get("email");
        } catch (Exception e) {
            return null;
        }
    }

    public String extractRole(String token) {
        Map<?, ?> claims = extractAllClaimsIfValid(token);
        if (claims == null) return null;
        try {
            String role = (String) claims.get("role");
            return role != null ? role : "ROLE_USER";
        } catch (Exception e) {
            return "ROLE_USER";
        }
    }

    private String sign(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Error signing HMAC token", e);
        }
    }
}
