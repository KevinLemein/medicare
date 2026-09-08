package com.medicare.identity.dto.request;

import com.medicare.identity.entity.Role;

import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull Role newRole) {}
