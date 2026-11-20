package com.example.spamer.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Выпускает подписанные JWT. В токене пользователя {@code sub} — его id, а роль лежит в
 * claim {@code role}. Пароль и другие персональные данные в токен не попадают.
 */
public class JwtTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties props;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder encoder, JwtProperties props) {
        this(encoder, props, Clock.systemUTC());
    }

    public JwtTokenIssuer(JwtEncoder encoder, JwtProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public IssuedToken issueForUser(UUID userId, String username, String role) {
        return issue(userId.toString(), username, role, props.accessTokenTtl());
    }

    /** Токен, которым сервис подписывает собственные вызовы к другим сервисам. */
    public IssuedToken issueForService(String serviceName) {
        return issue("service:" + serviceName, serviceName, Roles.SERVICE, props.serviceTokenTtl());
    }

    private IssuedToken issue(String subject, String username, String role, Duration ttl) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(subject)
                .claim(JwtSupport.USERNAME_CLAIM, username)
                .claim(JwtSupport.ROLE_CLAIM, role)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
