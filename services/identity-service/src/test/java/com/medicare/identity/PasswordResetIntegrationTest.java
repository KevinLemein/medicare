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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through the real "/accounts/password-reset/**" HTTP endpoints
 * -- previously only exercised indirectly through UserService unit-level
 * reasoning, never through the actual controllers/security chain.
 *
 * The raw reset token is never returned by the API (by design -- same
 * enumeration-safety rule the "always return the same response" comment in
 * PasswordController documents), so it's obtained the same way
 * ActivationAndLoginIntegrationTest obtains the activation token: by
 * calling UserService directly, then driving the rest of the flow through
 * real HTTP calls.
 */
@AutoConfigureMockMvc
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    private static final String OLD_PASSWORD = "correct-horse-battery-staple";
    private static final String NEW_PASSWORD = "a-different-strong-password-2";

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

    private String createAndActivateStaff(String email) throws Exception {
        User admin = createAdmin();
        UserService.StaffAccountResult created = userService.createStaffAccount(
                email, "Test", "Accountant", Role.ACCOUNTANT, admin.getId());

        mockMvc.perform(post("/accounts/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(created.rawActivationToken(), OLD_PASSWORD)))
                .andExpect(status().isOk());

        return created.user().getId().toString();
    }

    @Test
    void requestThenConfirm_resetsPasswordAndAllowsLoginWithNewPasswordOnly() throws Exception {
        String email = "accountant-" + UUID.randomUUID() + "@test.local";
        createAndActivateStaff(email);

        // Hits the real endpoint -- always the same response, whether or
        // not the account exists.
        mockMvc.perform(post("/accounts/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        Optional<String> rawToken = userService.requestPasswordReset(email);
        assertThat(rawToken).isPresent();

        mockMvc.perform(post("/accounts/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"%s"}
                                """.formatted(rawToken.get(), NEW_PASSWORD)))
                .andExpect(status().isOk());

        // Old password no longer works.
        mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", OLD_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("error")));

        // New password does.
        mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", NEW_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", not(containsString("error"))));
    }

    @Test
    void confirmWithAlreadyUsedToken_isRejected() throws Exception {
        String email = "accountant-" + UUID.randomUUID() + "@test.local";
        createAndActivateStaff(email);

        String rawToken = userService.requestPasswordReset(email).orElseThrow();

        mockMvc.perform(post("/accounts/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"%s"}
                                """.formatted(rawToken, NEW_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/accounts/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"another-strong-password-3"}
                                """.formatted(rawToken)))
                .andExpect(status().isBadRequest());
    }
}
