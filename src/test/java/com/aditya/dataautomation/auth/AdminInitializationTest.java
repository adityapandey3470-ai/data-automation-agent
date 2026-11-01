package com.aditya.dataautomation.auth;

import com.aditya.dataautomation.auth.config.AdminInitializer;
import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.config.properties.AdminProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AdminInitializationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("AdminInitializer hashes password with BCrypt, sets ROLE_ADMIN, and enables account")
    void testAdminInitializationPasswordHashingAndRole() {
        String testEmail = "superadmin_" + System.currentTimeMillis() + "@tabufinery.io";
        String rawPassword = "P@ssword" + System.currentTimeMillis() + "!";
        String adminName = "Platform Lead";

        AdminProperties props = new AdminProperties();
        props.setEmail(testEmail);
        props.setPassword(rawPassword);
        props.setName(adminName);

        MockEnvironment env = new MockEnvironment();
        AdminInitializer initializer = new AdminInitializer(userRepository, passwordEncoder, props, env);

        initializer.run(null);

        Optional<User> adminOpt = userRepository.findByEmailIgnoreCase(testEmail);
        assertThat(adminOpt).isPresent();

        User admin = adminOpt.get();
        assertThat(admin.getEmail()).isEqualTo(testEmail);
        assertThat(admin.getName()).isEqualTo(adminName);
        assertThat(admin.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(admin.isEnabled()).isTrue();

        // Verify password is NOT plaintext and matches BCrypt encoder
        assertThat(admin.getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(admin.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches(rawPassword, admin.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("AdminInitializer prevents duplicates and never overwrites an existing admin's password on restart")
    void testAdminDuplicatePreventionAndRestartPreservesPassword() {
        String testEmail = "preserveadmin_" + System.currentTimeMillis() + "@tabufinery.io";
        String initialPassword = "InitialPassword123!";
        String rotatedPassword = "RotatedPassword789!";

        AdminProperties props = new AdminProperties();
        props.setEmail(testEmail);
        props.setPassword(initialPassword);
        props.setName("System Admin");

        MockEnvironment env = new MockEnvironment();
        AdminInitializer initializer = new AdminInitializer(userRepository, passwordEncoder, props, env);

        // 1. Initial provisioning
        initializer.run(null);

        User created = userRepository.findByEmailIgnoreCase(testEmail).orElseThrow();
        assertThat(passwordEncoder.matches(initialPassword, created.getPasswordHash())).isTrue();

        // 2. Admin later updates their password via user profile
        created.setPasswordHash(passwordEncoder.encode(rotatedPassword));
        userRepository.saveAndFlush(created);

        // 3. Application restarts with the original initial credentials
        initializer.run(null);

        User afterRestart = userRepository.findByEmailIgnoreCase(testEmail).orElseThrow();

        // Must preserve the rotated password! Do not overwrite on restart
        assertThat(passwordEncoder.matches(rotatedPassword, afterRestart.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(initialPassword, afterRestart.getPasswordHash())).isFalse();

        // Ensure no duplicate accounts were created
        long count = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(testEmail))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("AdminInitializer halts startup if configured email belongs to a regular user and preserves existing account data")
    void testConfiguredAdminEmailMatchesRegularUserFailsStartupAndPreservesData() {
        String regularUserEmail = "regular_user_" + System.currentTimeMillis() + "@tabufinery.io";
        String regularUserPassword = "UserPassword123!";

        // 1. Create regular user in DB
        User regularUser = User.builder()
                .name("Standard Employee")
                .email(regularUserEmail)
                .passwordHash(passwordEncoder.encode(regularUserPassword))
                .role("ROLE_USER")
                .enabled(true)
                .build();
        userRepository.saveAndFlush(regularUser);

        // 2. Configure AdminProperties to point to this regular user's email
        AdminProperties props = new AdminProperties();
        props.setEmail(regularUserEmail);
        props.setPassword("AdminOverrideAttempt456!");
        props.setName("Hacker Attempt");

        MockEnvironment env = new MockEnvironment();
        AdminInitializer initializer = new AdminInitializer(userRepository, passwordEncoder, props, env);

        // 3. Execution MUST fail fast
        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Startup halted for security")
                .hasMessageContaining("Automatic privilege elevation of regular accounts is prohibited")
                .hasMessageContaining("ROLE_USER");

        // 4. Verify account data and credentials were fully preserved (NOT modified)
        User unchangedUser = userRepository.findByEmailIgnoreCase(regularUserEmail).orElseThrow();
        assertThat(unchangedUser.getRole()).isEqualTo("ROLE_USER");
        assertThat(unchangedUser.getName()).isEqualTo("Standard Employee");
        assertThat(passwordEncoder.matches(regularUserPassword, unchangedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("AdminOverrideAttempt456!", unchangedUser.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("AdminInitializer fails fast in production if no admin exists and credentials are missing")
    void testProductionMissingAdminFailsFast() {
        AdminProperties props = new AdminProperties();
        props.setEmail("");
        props.setPassword("");

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        // Mock a scenario where no admin exists in repo
        UserRepository mockRepo = org.mockito.Mockito.mock(UserRepository.class);
        org.mockito.Mockito.when(mockRepo.existsByRole("ROLE_ADMIN")).thenReturn(false);

        AdminInitializer initializer = new AdminInitializer(mockRepo, passwordEncoder, props, prodEnv);

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production startup failure: No administrator exists in the database")
                .hasMessageContaining("APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD environment variables are required");
    }
}
