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

        boolean isProduction = environment.acceptsProfiles(Profiles.of("prod", "production"));

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

        String email = adminEmail.trim().toLowerCase();
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);

        if (existing.isPresent()) {
            User user = existing.get();
            // Prohibit automatic privilege escalation of existing regular accounts
            if (!"ROLE_ADMIN".equals(user.getRole())) {
                throw new IllegalStateException(
                        String.format("Startup halted for security: The configured admin email '%s' already belongs to an existing user with role '%s'. " +
                                "Automatic privilege elevation of regular accounts is prohibited. " +
                                "Please specify a different APP_ADMIN_EMAIL or perform role elevation via explicit manual intervention.",
                                email, user.getRole())
                );
            }
            log.info("Administrator {} is already initialized. Existing password and account data preserved.", email);
        } else {
            // Provision initial administrator with BCrypt password hash
            User admin = User.builder()
                    .email(email)
                    .name(adminName != null && !adminName.isBlank() ? adminName.trim() : "System Administrator")
                    .passwordHash(passwordEncoder.encode(adminPassword.trim()))
                    .role("ROLE_ADMIN")
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("Secure initial administrator provisioned with ROLE_ADMIN for email={}", email);
        }
    }
}
