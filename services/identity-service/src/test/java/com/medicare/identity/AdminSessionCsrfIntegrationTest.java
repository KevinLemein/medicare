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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demonstrates the actual vulnerability item #3 fixes, and that the fix
 * doesn't break the legitimate Bearer-JWT admin-tooling path.
 *
 * Before SessionCsrfRequirementMatcher, "/admin/**" was blanket-exempted
 * from CSRF on the (incorrect) assumption that it's only ever called with
 * a Bearer token. But SecurityConfig's chain also accepts a plain browser
 * session from the hosted /login page (formLogin() is on the very same
 * chain) -- so a SYSTEM_ADMIN with an authenticated browser session on
 * this origin was, before this fix, one forged same-site POST away from a
 * cross-site request silently suspending/deactivating/changing the role
 * of any account, since the browser attaches the session cookie
 * automatically and no CSRF token was ever demanded.
 */
@AutoConfigureMockMvc
class AdminSessionCsrfIntegrationTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private User createActiveAdmin(String rawPassword) throws Exception {
        User bootstrapAdmin = new User();
        bootstrapAdmin.setEmail("bootstrap-" + UUID.randomUUID() + "@test.local");
        bootstrapAdmin.setFirstName("Sys");
        bootstrapAdmin.setLastName("Admin");
        bootstrapAdmin.setRole(Role.SYSTEM_ADMIN);
        bootstrapAdmin.setStatus(AccountStatus.ACTIVE);
        userRepository.save(bootstrapAdmin);

        String adminEmail = "admin-" + UUID.randomUUID() + "@test.local";
        UserService.StaffAccountResult created = userService.createStaffAccount(
                adminEmail, "Test", "Admin", Role.SYSTEM_ADMIN, bootstrapAdmin.getId());

        mockMvc.perform(post("/accounts/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(created.rawActivationToken(), rawPassword)))
                .andExpect(status().isOk());

        return userRepository.findById(created.user().getId()).orElseThrow();
    }

    private User createActiveStaff() {
        User user = new User();
        user.setEmail("nurse-" + UUID.randomUUID() + "@test.local");
        user.setFirstName("Test");
        user.setLastName("Nurse");
        user.setRole(Role.NURSE);
        user.setStatus(AccountStatus.ACTIVE);
        return userRepository.save(user);
    }

    @Test
    void sessionAuthenticatedAdminCall_withoutCsrfToken_isRejected() throws Exception {
        User admin = createActiveAdmin(RAW_PASSWORD);
        User target = createActiveStaff();

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", admin.getEmail())
                        .param("password", RAW_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Simulates a forged cross-site POST riding on the admin's
        // authenticated browser session cookie: same session, no CSRF
        // token attached (a cross-origin page has no way to read one).
        mockMvc.perform(post("/admin/users/{userId}/suspend", target.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionAuthenticatedAdminCall_withValidCsrfToken_succeeds() throws Exception {
        User admin = createActiveAdmin(RAW_PASSWORD);
        User target = createActiveStaff();

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", admin.getEmail())
                        .param("password", RAW_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/admin/users/{userId}/suspend", target.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void bearerJwtAdminCall_neverNeedsACsrfToken() throws Exception {
        User admin = new User();
        admin.setEmail("tooling-admin-" + UUID.randomUUID() + "@test.local");
        admin.setFirstName("Tooling");
        admin.setLastName("Admin");
        admin.setRole(Role.SYSTEM_ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        userRepository.save(admin);

        User target = createActiveStaff();

        // No session at all -- a stateless Bearer caller, exactly how
        // admin tooling and the SPA actually call this endpoint.
        mockMvc.perform(post("/admin/users/{userId}/suspend", target.getId())
                        .with(jwt()
                                .jwt(token -> token.subject(admin.getEmail()).claim("role", "SYSTEM_ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
