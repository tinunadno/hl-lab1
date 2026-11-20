package com.example.spamer.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtSupportTest {

    static final String SECRET = "unit-test-secret-0123456789-abcdefgh";

    final JwtProperties props =
            new JwtProperties(SECRET, "spamer", Duration.ofHours(1), Duration.ofMinutes(5));
    final JwtTokenIssuer issuer = new JwtTokenIssuer(JwtSupport.encoder(props), props);

    @Test
    void userTokenRoundTripsWithRoleAndWithoutSecrets() {
        UUID id = UUID.randomUUID();
        String token = issuer.issueForUser(id, "alice", Roles.USER_PRO).value();

        Jwt jwt = JwtSupport.decoder(props).decode(token);
        CurrentUser user = CurrentUser.from(jwt);

        assertThat(user).isEqualTo(new CurrentUser(id, "alice", Roles.USER_PRO));
        assertThat(jwt.getClaims()).containsOnlyKeys("iss", "iat", "exp", "sub", "username", "role");
        assertThat(JwtSupport.reactiveDecoder(props).decode(token).block().getSubject())
                .isEqualTo(id.toString());
    }

    @Test
    void roleBecomesSpringAuthority() {
        Jwt jwt = JwtSupport.decoder(props).decode(
                issuer.issueForUser(UUID.randomUUID(), "bob", Roles.SUPERVISOR).value());

        JwtAuthenticationToken auth =
                (JwtAuthenticationToken) JwtSupport.authenticationConverter().convert(jwt);

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SUPERVISOR");
        assertThat(auth.getName()).isEqualTo(jwt.getSubject());
    }

    @Test
    void tokenWithoutRoleHasNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("x").build();

        assertThat(JwtSupport.authorities(jwt)).isEmpty();
    }

    @Test
    void tokenFromAnotherSecretIsRejected() {
        JwtProperties other = new JwtProperties(
                "another-secret-0123456789-abcdefghij", "spamer", Duration.ofHours(1), Duration.ofMinutes(5));
        String forged = new JwtTokenIssuer(JwtSupport.encoder(other), other)
                .issueForUser(UUID.randomUUID(), "eve", Roles.SUPERVISOR).value();

        assertThatThrownBy(() -> JwtSupport.decoder(props).decode(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC);
        String old = new JwtTokenIssuer(JwtSupport.encoder(props), props, past)
                .issueForUser(UUID.randomUUID(), "old", Roles.USER_FREE).value();

        assertThatThrownBy(() -> JwtSupport.decoder(props).decode(old))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void foreignIssuerIsRejected() {
        JwtProperties foreign =
                new JwtProperties(SECRET, "someone-else", Duration.ofHours(1), Duration.ofMinutes(5));
        String token = new JwtTokenIssuer(JwtSupport.encoder(foreign), foreign)
                .issueForUser(UUID.randomUUID(), "x", Roles.USER_FREE).value();

        assertThatThrownBy(() -> JwtSupport.decoder(props).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtProperties("short", "spamer", Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(null, "spamer", Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void serviceTokenHasServiceRoleAndNoUserId() {
        Jwt jwt = JwtSupport.decoder(props).decode(issuer.issueForService("dispatch-service").value());
        CurrentUser caller = CurrentUser.from(jwt);

        assertThat(caller.id()).isNull();
        assertThat(caller.isService()).isTrue();
        assertThat(caller.canSeeEveryone()).isTrue();
        assertThat(caller.isSupervisor()).isFalse();
        assertThat(caller.owns(UUID.randomUUID())).isFalse();
    }

    @Test
    void issuerConfigBuildsWorkingIssuer() {
        String token = new JwtIssuerConfig().jwtTokenIssuer(props)
                .issueForUser(UUID.randomUUID(), "c", Roles.VICTIM).value();

        assertThat(CurrentUser.from(JwtSupport.decoder(props).decode(token)).is(Roles.VICTIM)).isTrue();
    }
}
