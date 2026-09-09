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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full authorization_code + PKCE grant, exactly as the hms-frontend SPA
 * uses it: hosted login (browser session) -> GET /oauth2/authorize (no
 * separate consent screen, since hms-frontend is a first-party client
 * registered with requireAuthorizationConsent(false) -- approval is
 * implicit) -> authorization code -> POST /oauth2/token (stateless, PKCE
 * code_verifier as proof, no client secret) -> the returned access token
 * used as a Bearer credential against a real authenticated API endpoint.
 *
 * Before this test, the grant had never been exercised end-to-end -- unit
 * tests only cover the login form and lockout tracking. This is also what
 * caught the missing CSRF exemption on the token endpoint's filter chain
 * (see AuthorizationServerConfig / SessionCsrfRequirementMatcher): a naive
 * "CSRF enabled everywhere" default would fail the POST /oauth2/token step
 * below, since a real PKCE public-client token exchange never carries a
 * session-bound CSRF token.
 */
@AutoConfigureMockMvc
class OAuth2AuthorizationCodePkceFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "correct-horse-battery-staple";
    private static final String CLIENT_ID = "hms-frontend";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

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

    @Test
    void loginToAuthenticatedApiCall_succeedsThroughRealPkceGrant() throws Exception {
        User admin = createAdmin();
        String staffEmail = "doctor-" + UUID.randomUUID() + "@test.local";

        UserService.StaffAccountResult created = userService.createStaffAccount(
                staffEmail, "Test", "Doctor", Role.DOCTOR, admin.getId());

        mockMvc.perform(post("/accounts/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(created.rawActivationToken(), RAW_PASSWORD)))
                .andExpect(status().isOk());

        String accessToken = OAuth2FlowTestSupport.obtainAccessToken(
                mockMvc, staffEmail, RAW_PASSWORD, CLIENT_ID, redirectUri);

        // A real, state-changing authenticated API request using the token
        // this same grant just produced -- proves the access token is
        // actually accepted by the resource-server side of this service,
        // not just issued.
        mockMvc.perform(post("/accounts/password/change")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"a-new-strong-password-1"}
                                """.formatted(RAW_PASSWORD)))
                .andExpect(status().isOk());
    }
}
