package com.example.spamer.user.dto;

import java.time.Instant;
import java.util.UUID;

/** Ответ на логин: токен передаётся дальше в заголовке {@code Authorization: Bearer <token>}. */
public record TokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UUID userId,
        String role) {
}
