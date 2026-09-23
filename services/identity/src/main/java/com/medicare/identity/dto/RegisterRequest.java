package com.medicare.identity.dto;

import com.medicare.identity.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest (
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
        @NotNull Role role
){}

