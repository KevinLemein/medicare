package com.medicare.identity.controller;

import com.medicare.identity.AbstractIntegrationTest;
import com.medicare.identity.entity.AccountStatus;
import com.medicare.identity.entity.Role;
import com.medicare.identity.entity.User;
import com.medicare.identity.repository.UserRepository;
import com.medicare.identity.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the "/admin/users/**" lifecycle actions.
 * Authenticates as a Bearer-JWT SYSTEM_ADMIN the same way
 * PatientProvisioningSecurityTest authenticates as a client-credentials
 * caller -- this is exactly how admin tooling calls these endpoints in
 * practice, and confirms the CSRF policy change in SecurityConfig
 * (SessionCsrfRequirementMatcher) doesn't get in the way of a stateless
 * Bearer caller (no session -> no CSRF token required).
 */
@AutoConfigureMockMvc
class AdminUserControllerIntegrationTest extends AbstractIntegrationTest {

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

    private User createActiveStaff(Role role) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        user.setFirstName("Test");
        user.setLastName(role.name());
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        return userRepository.save(user);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asAdmin(String adminEmail) {
        return jwt()
                .jwt(token -> token.subject(adminEmail).claim("role", "SYSTEM_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
    }

    @Test
    void changeRole_updatesRoleAndReturnsNewState() throws Exception {
        User admin = createAdmin();
        User target = createActiveStaff(Role.NURSE);

        mockMvc.perform(post("/admin/users/{userId}/role", target.getId())
                        .with(asAdmin(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newRole":"DOCTOR"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("DOCTOR"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void suspendThenReactivate_transitionsStatusBothWays() throws Exception {
        User admin = createAdmin();
        User target = createActiveStaff(Role.RECEPTIONIST);

        mockMvc.perform(post("/admin/users/{userId}/suspend", target.getId())
                        .with(asAdmin(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"policy violation"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.SUSPENDED);

        mockMvc.perform(post("/admin/users/{userId}/reactivate", target.getId())
                        .with(asAdmin(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void deactivateThenReactivateFromDeactivated_requiresAReason() throws Exception {
        User admin = createAdmin();
        User target = createActiveStaff(Role.PHARMACIST);

        mockMvc.perform(post("/admin/users/{userId}/deactivate", target.getId())
                        .with(asAdmin(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"left the organization"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEACTIVATED"));

        // No reason -> rejected before the service layer even runs the transition.
        mockMvc.perform(post("/admin/users/{userId}/reactivate-from-deactivated", target.getId())
                        .with(asAdmin(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":""}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/admin/users/{userId}/reactivate-from-deactivated", target.getId())
                        .with(asAdmin(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"rehired"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void unlock_clearsLockoutState() throws Exception {
        User admin = createAdmin();
        User target = createActiveStaff(Role.LAB_TECHNICIAN);

        for (int i = 0; i < 5; i++) {
            userService.recordFailedLoginAttempt(target.getEmail());
        }
        assertThat(userRepository.findById(target.getId()).orElseThrow().getLockedUntil()).isNotNull();

        mockMvc.perform(post("/admin/users/{userId}/unlock", target.getId())
                        .with(asAdmin(admin.getEmail())))
                .andExpect(status().isOk());

        User unlocked = userRepository.findById(target.getId()).orElseThrow();
        assertThat(unlocked.getLockedUntil()).isNull();
        assertThat(unlocked.getFailedLoginCount()).isZero();
    }

    @Test
    void nonAdminCaller_isForbiddenFromAdminEndpoints() throws Exception {
        User target = createActiveStaff(Role.NURSE);

        mockMvc.perform(post("/admin/users/{userId}/suspend", target.getId())
                        .with(jwt()
                                .jwt(token -> token.subject("nurse@test.local").claim("role", "NURSE"))
                                .authorities(new SimpleGrantedAuthority("ROLE_NURSE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
