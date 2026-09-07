package com.medicare.identity.service;

import com.medicare.identity.entity.AccountStatus;
import com.medicare.identity.entity.User;
import com.medicare.identity.repository.PasswordCredentialRepository;
import com.medicare.identity.repository.UserRepository;


import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class IdentityUserDetailsService implements UserDetailsService{

    // Guaranteed to never match a real submitted password — used only for
    // PENDING_ACTIVATION accounts with no credential yet. In practice this
    // is never actually compared: Spring Security's pre-authentication
    // checks reject a disabled account (see below) before password
    // matching runs at all — worth a quick test to confirm on this
    // specific Spring Security version rather than assuming.
    private static final String NO_CREDENTIAL_PLACEHOLDER = "unset";

    private final UserRepository userRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;

    public IdentityUserDetailsService(UserRepository userRepository,
                                      PasswordCredentialRepository passwordCredentialRepository) {
        this.userRepository = userRepository;
        this.passwordCredentialRepository = passwordCredentialRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));

        String passwordHash = passwordCredentialRepository.findByUserId(user.getId())
                .map(c -> c.getPasswordHash())
                .orElse(NO_CREDENTIAL_PLACEHOLDER);

        boolean lockedOut = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(passwordHash)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .accountLocked(lockedOut)
                .disabled(user.getStatus() != AccountStatus.ACTIVE)
                .build();
    }
}
