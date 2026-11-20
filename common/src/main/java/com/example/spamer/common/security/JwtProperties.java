package com.example.spamer.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки JWT, одинаковые во всех сервисах.
 *
 * @param secret          общий секрет HMAC-SHA256, не короче 32 байт
 * @param issuer          значение claim {@code iss}; токены чужого издателя отклоняются
 * @param accessTokenTtl  срок жизни токена пользователя
 * @param serviceTokenTtl срок жизни токена для вызовов между сервисами
 */
@ConfigurationProperties("spamer.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("spamer") String issuer,
        @DefaultValue("1h") Duration accessTokenTtl,
        @DefaultValue("5m") Duration serviceTokenTtl) {

    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("spamer.security.jwt.secret must be at least "
                    + MIN_SECRET_BYTES + " bytes (set JWT_SECRET)");
        }
    }
}
