package com.example.spamer.common.feign;

import com.example.spamer.common.security.JwtTokenIssuer;
import com.example.spamer.common.security.JwtTokenIssuer.IssuedToken;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Подписывает каждый Feign-запрос служебным JWT с ролью {@code SERVICE}. Так вызовы между
 * сервисами работают и без пользователя рядом: например, повторы возвратов по расписанию.
 * Токен переиспользуется, пока до его истечения больше 30 секунд.
 */
public class ServiceTokenInterceptor implements RequestInterceptor {

    static final Duration REFRESH_MARGIN = Duration.ofSeconds(30);

    private final JwtTokenIssuer issuer;
    private final String serviceName;
    private final Clock clock;
    private final AtomicReference<IssuedToken> current = new AtomicReference<>();

    public ServiceTokenInterceptor(JwtTokenIssuer issuer, String serviceName) {
        this(issuer, serviceName, Clock.systemUTC());
    }

    ServiceTokenInterceptor(JwtTokenIssuer issuer, String serviceName, Clock clock) {
        this.issuer = issuer;
        this.serviceName = serviceName;
        this.clock = clock;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.removeHeader("Authorization");
        template.header("Authorization", "Bearer " + token());
    }

    String token() {
        IssuedToken token = current.get();
        if (token == null || clock.instant().plus(REFRESH_MARGIN).isAfter(token.expiresAt())) {
            token = issuer.issueForService(serviceName);
            current.set(token);
        }
        return token.value();
    }
}
