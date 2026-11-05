package com.example.spamer.user.dto;

import com.example.spamer.user.domain.UserEntity;
import com.example.spamer.user.domain.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        UserRole role,
        BigDecimal balance,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(UserEntity u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getRole(),
                u.getBalance(),
                u.getCreatedAt() == null ? null : u.getCreatedAt().toInstant(),
                u.getUpdatedAt() == null ? null : u.getUpdatedAt().toInstant());
    }
}
