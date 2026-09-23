package com.medicare.identity.dto;

import com.medicare.identity.entity.Role;
import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        Role role,
        String accessToken
) {}