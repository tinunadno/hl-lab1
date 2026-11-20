package com.example.spamer.common.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spamer.common.security.CurrentUser;
import com.example.spamer.common.security.JwtProperties;
import com.example.spamer.common.security.JwtSupport;
import com.example.spamer.common.security.JwtTokenIssuer;
import feign.RequestTemplate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ServiceTokenInterceptorTest {

    final JwtProperties props = new JwtProperties(
            "unit-test-secret-0123456789-abcdefgh", "spamer", Duration.ofHours(1), Duration.ofMinutes(5));

    @Test
    void addsServiceBearerTokenReplacingAnyExistingOne() {
        JwtTokenIssuer issuer = new JwtTokenIssuer(JwtSupport.encoder(props), props);
        RequestTemplate template = new RequestTemplate();
        template.header("Authorization", "Bearer user-token");

        new ServiceAuthFeignConfig().serviceTokenInterceptor(issuer, "dispatch-service").apply(template);

        assertThat(template.headers().get("Authorization")).singleElement()
                .satisfies(value -> {
                    assertThat(value).startsWith("Bearer ");
                    CurrentUser caller = CurrentUser.from(
                            JwtSupport.decoder(props).decode(value.substring("Bearer ".length())));
                    assertThat(caller.isService()).isTrue();
                    assertThat(caller.username()).isEqualTo("dispatch-service");
                });
    }

    @Test
    void reusesTokenUntilItIsAboutToExpire() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2030-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        JwtTokenIssuer issuer = new JwtTokenIssuer(JwtSupport.encoder(props), props, clock);
        ServiceTokenInterceptor interceptor = new ServiceTokenInterceptor(issuer, "svc", clock);

        String first = interceptor.token();
        now.set(now.get().plus(Duration.ofMinutes(4)));
        assertThat(interceptor.token()).isEqualTo(first);

        now.set(now.get().plus(Duration.ofSeconds(40)));
        assertThat(interceptor.token()).isNotEqualTo(first);
    }
}
