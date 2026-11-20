package com.example.spamer.user.security;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * BCrypt намеренно медленный (десятки миллисекунд), поэтому хэширование и проверка
 * выполняются на {@code boundedElastic}, а не на event loop.
 */
@Component
public class PasswordHasher {

    private final PasswordEncoder encoder;

    /**
     * Хэш случайной строки. С ним сравнивается пароль, когда пользователя нет: тогда ответ на
     * неверное имя занимает столько же времени, сколько на неверный пароль.
     */
    private final String dummyHash;

    public PasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public Mono<String> hash(String rawPassword) {
        return Mono.fromCallable(() -> encoder.encode(rawPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> matches(String rawPassword, String hash) {
        String target = hash == null ? dummyHash : hash;
        return Mono.fromCallable(() -> encoder.matches(rawPassword, target))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
