package com.medicare.identity.security;

import com.medicare.identity.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Wraps the default hosted-login-page failure behavior (redirect back to
 * /login?error) with lockout tracking. Fires for both "no such user" and
 * "wrong password" — recordFailedLoginAttempt already handles the unknown-
 * email case safely (§16: still audited, no account to lock).
 */
@Component
public class LoginAuditAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final UserService userService;

    public LoginAuditAuthenticationFailureHandler(UserService userService) {
        super("/login?error");
        this.userService = userService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        // Default Spring Security form-login parameter name.
        String submittedUsername = request.getParameter("username");
        if (submittedUsername != null && !submittedUsername.isBlank()) {
            userService.recordFailedLoginAttempt(submittedUsername);
        }

        super.onAuthenticationFailure(request, response, exception);
    }
}
