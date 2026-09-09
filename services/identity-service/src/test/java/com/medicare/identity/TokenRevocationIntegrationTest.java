package com.medicare.identity;

import com.medicare.identity.entity.AccountStatus;
import com.medicare.identity.entity.Role;
import com.medicare.identity.entity.User;
import com.medicare.identity.repository.UserRepository;
import com.medicare.identity.service.UserService;
import com.medicare.identity.support.OAuth2FlowTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms that the four triggers wired to TokenRevocationService
 * (password reset, password change, role change, suspend/deactivate)
 * actually remove the OAuth2 authorization/token rows they're supposed
 * to -- not just that UserService calls the revocation method (that's
 * covered by the unit-level reasoning in UserService itself), but that a
 * real access/refresh token issued through the real
 * authorization_code + PKCE grant genuinely disappears from
 * oauth2_authorization afterward.
 */
@AutoConfigureMockMvc
class TokenRevocationIntegrationTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "correct-horse-battery-staple";
    private static final String CLIENT_ID = "hms-frontend";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${identity.oauth2.frontend-redirect-uri}")
    private String redirectUri;

    private User createAdmin() {
        User admin = new User();
        admin.setEmail("admin-" + UUID.randomUUID() + "@test.local");
        admin.setFirstName("Sys");
        admin.setLastName("Admin");
        admin.setRole(Role.SYSTEM_ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        return userRepository.save(admin);
    }

    private User createAndActivateStaff(User admin, Role role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        UserService.StaffAccountResult created = userService.createStaffAccount(
                email, "Test", role.name(), role, admin.getId());

        mockMvc.perform(post("/accounts/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(created.rawActivationToken(), RAW_PASSWORD)))
                .andExpect(status().isOk());

        return userRepository.findById(created.user().getId()).orElseThrow();
    }

    private int authorizationCountFor(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM oauth2_authorization WHERE principal_name = ?", Integer.class, email);
        return count == null ? 0 : count;
    }

    @Test
    void changePassword_revokesExistingAuthorizations() throws Exception {
        User admin = createAdmin();
        User user = createAndActivateStaff(admin, Role.NURSE);
        OAuth2FlowTestSupport.obtainAccessToken(mockMvc, user.getEmail(), RAW_PASSWORD, CLIENT_ID, redirectUri);
        assertThat(authorizationCountFor(user.getEmail())).isGreaterThan(0);

        userService.changePassword(user.getId(), RAW_PASSWORD, "brand-new-strong-password-1");

        assertThat(authorizationCountFor(user.getEmail())).isZero();
    }

    @Test
    void completePasswordReset_revokesExistingAuthorizations() throws Exception {
        User admin = createAdmin();
        User user = createAndActivateStaff(admin, Role.RECEPTIONIST);
        OAuth2FlowTestSupport.obtainAccessToken(mockMvc, user.getEmail(), RAW_PASSWORD, CLIENT_ID, redirectUri);
        assertThat(authorizationCountFor(user.getEmail())).isGreaterThan(0);

        String rawToken = userService.requestPasswordReset(user.getEmail()).orElseThrow();
        userService.completePasswordReset(rawToken, "brand-new-strong-password-2");

        assertThat(authorizationCountFor(user.getEmail())).isZero();
    }

    @Test
    void changeRole_revokesExistingAuthorizations() throws Exception {
        User admin = createAdmin();
        User user = createAndActivateStaff(admin, Role.PHARMACIST);
        OAuth2FlowTestSupport.obtainAccessToken(mockMvc, user.getEmail(), RAW_PASSWORD, CLIENT_ID, redirectUri);
        assertThat(authorizationCountFor(user.getEmail())).isGreaterThan(0);

        userService.changeRole(user.getId(), admin.getId(), Role.DOCTOR);

        assertThat(authorizationCountFor(user.getEmail())).isZero();
    }

    @Test
    void suspend_revokesExistingAuthorizations() throws Exception {
        User admin = createAdmin();
        User user = createAndActivateStaff(admin, Role.LAB_TECHNICIAN);
        OAuth2FlowTestSupport.obtainAccessToken(mockMvc, user.getEmail(), RAW_PASSWORD, CLIENT_ID, redirectUri);
        assertThat(authorizationCountFor(user.getEmail())).isGreaterThan(0);

        userService.suspend(user.getId(), admin.getId(), "policy violation");

        assertThat(authorizationCountFor(user.getEmail())).isZero();
    }

    @Test
    void deactivate_revokesExistingAuthorizations() throws Exception {
        User admin = createAdmin();
        User user = createAndActivateStaff(admin, Role.ACCOUNTANT);
        OAuth2FlowTestSupport.obtainAccessToken(mockMvc, user.getEmail(), RAW_PASSWORD, CLIENT_ID, redirectUri);
        assertThat(authorizationCountFor(user.getEmail())).isGreaterThan(0);

        userService.deactivate(user.getId(), admin.getId(), "left the organization");

        assertThat(authorizationCountFor(user.getEmail())).isZero();
    }

    @Test
    void reactivate_doesNotRevokeAnything() throws Exception {
        User admin = createAdmin();
        User user = createAndActivateStaff(admin, Role.DOCTOR);
        OAuth2FlowTestSupport.obtainAccessToken(mockMvc, user.getEmail(), RAW_PASSWORD, CLIENT_ID, redirectUri);
        userService.suspend(user.getId(), admin.getId(), "temporary");
        assertThat(authorizationCountFor(user.getEmail())).isZero();

        // Reactivating restores access -- nothing left to revoke, and the
        // call must not blow up on an already-empty set of authorizations.
        userService.reactivate(user.getId(), admin.getId());

        assertThat(authorizationCountFor(user.getEmail())).isZero();
    }
}
