package com.medicare.identity;

import com.medicare.identity.entity.AccountStatus;
import com.medicare.identity.entity.Role;
import com.medicare.identity.entity.User;
import com.medicare.identity.repository.UserRepository;
import com.medicare.identity.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through the real HTTP endpoints for the two public flows
 * (activate, hosted login) that plain unit tests can't exercise: Spring
 * Security's form-login pipeline and the lockout-tracking handlers wired
 * into it.
 */
@AutoConfigureMockMvc
class ActivationAndLoginIntegrationTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private User createAdmin() {
        User admin = new User();
        admin.setEmail("admin-" + UUID.randomUUID() + "@test.local");
        admin.setFirstName("Sys");
        admin.setLastName("Admin");
        admin.setRole(Role.SYSTEM_ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        return userRepository.save(admin);
    }

    @Test
    void createActivateThenLogin_succeedsAndRecordsLogin() throws Exception {
        User admin = createAdmin();
        String staffEmail = "nurse-" + UUID.randomUUID() + "@test.local";

        UserService.StaffAccountResult created = userService.createStaffAccount(
                staffEmail, "Test", "Nurse", Role.NURSE, admin.getId());

        mockMvc.perform(post("/accounts/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(created.rawActivationToken(), RAW_PASSWORD)))
                .andExpect(status().isOk());

        User activated = userRepository.findById(created.user().getId()).orElseThrow();
        assertThat(activated.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        mockMvc.perform(post("/login")
                        .param("username", staffEmail)
                        .param("password", RAW_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        User loggedIn = userRepository.findById(created.user().getId()).orElseThrow();
        assertThat(loggedIn.getLastLoginAt()).isNotNull();
        assertThat(loggedIn.getFailedLoginCount()).isZero();
    }

    @Test
    void loginWithWrongPassword_recordsFailedAttemptAndRedirectsToLoginError() throws Exception {
        User admin = createAdmin();
        String staffEmail = "receptionist-" + UUID.randomUUID() + "@test.local";

        UserService.StaffAccountResult created = userService.createStaffAccount(
                staffEmail, "Test", "Receptionist", Role.RECEPTIONIST, admin.getId());

        mockMvc.perform(post("/accounts/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(created.rawActivationToken(), RAW_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/login")
                        .param("username", staffEmail)
                        .param("password", "definitely-wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        User afterFailedLogin = userRepository.findById(created.user().getId()).orElseThrow();
        assertThat(afterFailedLogin.getFailedLoginCount()).isEqualTo(1);
        assertThat(afterFailedLogin.getLastLoginAt()).isNull();
    }

    @Test
    void repeatedFailedLogins_locksAccountAndBlocksCorrectPasswordToo() throws Exception {
        User admin = createAdmin();
        String staffEmail = "pharmacist-" + UUID.randomUUID() + "@test.local";

        UserService.StaffAccountResult created = userService.createStaffAccount(
                staffEmail, "Test", "Pharmacist", Role.PHARMACIST, admin.getId());

        mockMvc.perform(post("/accounts/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(created.rawActivationToken(), RAW_PASSWORD)))
                .andExpect(status().isOk());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/login")
                            .param("username", staffEmail)
                            .param("password", "definitely-wrong-password")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        User lockedOut = userRepository.findById(created.user().getId()).orElseThrow();
        assertThat(lockedOut.getLockedUntil()).isNotNull();

        // Even the correct password is now rejected while locked.
        mockMvc.perform(post("/login")
                        .param("username", staffEmail)
                        .param("password", RAW_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        User stillLocked = userRepository.findById(created.user().getId()).orElseThrow();
        assertThat(stillLocked.getLastLoginAt()).isNull();
    }
}
