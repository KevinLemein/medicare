package com.medicare.identity.security;

import com.medicare.identity.repository.UserRepository;
import com.medicare.identity.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Wraps the default hosted-login-page success behavior (redirect back to
 * the saved request, e.g. the in-flight /oauth2/authorize) with the
 * lockout-tracking side effect that plain formLogin() never triggered on
 * its own.
 */
@Component
public class LoginAuditAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserService userService;
    private final UserRepository userRepository;

    public LoginAuditAuthenticationSuccessHandler(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        // authentication.getName() is the UserDetails username, which
        // IdentityUserDetailsService sets to the already-normalized
        // user.getEmail() — safe to look up directly.
        userRepository.findByEmail(authentication.getName())
                .ifPresent(user -> userService.recordSuccessfulLogin(user.getId()));

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
