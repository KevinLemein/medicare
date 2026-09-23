package com.medicare.identity.service;

import com.medicare.identity.common.DuplicateResourceException;
import com.medicare.identity.dto.AuthResponse;
import com.medicare.identity.dto.LoginRequest;
import com.medicare.identity.dto.RegisterRequest;
import com.medicare.identity.entity.User;
import com.medicare.identity.repository.UserRepository;
import com.medicare.identity.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        userRepository.save(user);
        String token = jwtService.issueToken(user);
        return new AuthResponse(user.getId(), user.getEmail(), user.getRole(), token);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        String token = jwtService.issueToken(user);
        return new AuthResponse(user.getId(), user.getEmail(), user.getRole(), token);
    }

}
