package com.aditya.dataautomation.auth.config;

import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.config.properties.AdminProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String adminEmail = adminProperties.getEmail();
        String adminPassword = adminProperties.getPassword();
        String adminName = adminProperties.getName();

        // Fail-safe: anything that is not explicitly a dev/local/test profile is treated as production
        boolean devMode = environment.acceptsProfiles(Profiles.of("dev", "local", "test"));
        boolean isProduction = !devMode;

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            boolean hasAdmin = userRepository.existsByRole("ROLE_ADMIN");
            if (isProduction && !hasAdmin) {
                throw new IllegalStateException(
                        "Production startup failure: No administrator exists in the database. " +
                        "APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD environment variables are required for initial administrator creation."
                );
            }
            log.info("No initial admin credentials provided via APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD. System running with existing accounts.");
            return;
        }

        if (isProduction && adminPassword.trim().length() < 12) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD must be at least 12 characters outside dev/local/test profiles.");
        }

        String email = adminEmail.trim().toLowerCase();
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);

        if (existing.isPresent()) {
            User user = existing.get();

            if (!"ROLE_ADMIN".equals(user.getRole())) {
                if (isProduction) {
                    throw new IllegalStateException(
                            String.format("Startup halted for security: The configured admin email '%s' already belongs to an existing user with role '%s'. " +
                                    "Automatic privilege elevation of regular accounts is prohibited in production. " +
                                    "Please specify a different APP_ADMIN_EMAIL or perform role elevation via explicit manual intervention.",
                                    email, user.getRole())
                    );
                } else {
                    user.setRole("ROLE_ADMIN");
                    if (adminPassword != null && !adminPassword.isBlank()) {
                        user.setPasswordHash(passwordEncoder.encode(adminPassword.trim()));
                    }
                    userRepository.save(user);
                    log.info("Elevated existing account '{}' to ROLE_ADMIN in development mode.", email);
                }
            } else {
                log.info("Administrator {} is already initialized. Existing password and account data preserved.", email);
            }
        } else {

            User admin = User.builder()
                    .email(email)
                    .name(adminName != null && !adminName.isBlank() ? adminName.trim() : "System Administrator")
                    .passwordHash(passwordEncoder.encode(adminPassword.trim()))
                    .role("ROLE_ADMIN")
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            userRepository.save(admin);
            log.info("Secure initial administrator provisioned with ROLE_ADMIN for email={}", email);
        }
    }
}
