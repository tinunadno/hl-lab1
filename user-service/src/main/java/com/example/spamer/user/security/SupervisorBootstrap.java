package com.example.spamer.user.security;

import com.example.spamer.user.domain.UserEntity;
import com.example.spamer.user.domain.UserRepository;
import com.example.spamer.user.domain.UserRole;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Пользователей создают только супервайзеры, поэтому первого из них нужно откуда-то взять.
 * При старте, если в базе нет ни одного супервайзера, он создаётся из
 * {@code SUPERVISOR_USERNAME}/{@code SUPERVISOR_PASSWORD}. Если супервайзер уже есть, ничего
 * не происходит, и пароль из переменных окружения больше ни на что не влияет.
 */
@Component
@RequiredArgsConstructor
public class SupervisorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SupervisorBootstrap.class);

    private final UserRepository repo;
    private final R2dbcEntityTemplate template;
    private final PasswordHasher passwords;

    @Value("${spamer.security.bootstrap.username:}")
    private String username;

    @Value("${spamer.security.bootstrap.password:}")
    private String password;

    @Value("${spamer.security.bootstrap.email:supervisor@spamer.local}")
    private String email;

    @Override
    public void run(ApplicationArguments args) {
        ensureSupervisor().block();
    }

    Mono<Boolean> ensureSupervisor() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.info("Supervisor bootstrap is off: SUPERVISOR_USERNAME/SUPERVISOR_PASSWORD not set");
            return Mono.just(false);
        }
        return repo.existsWithRole(UserRole.SUPERVISOR.name()).flatMap(exists -> {
            if (exists) {
                return Mono.just(false);
            }
            return passwords.hash(password).flatMap(hash -> {
                UserEntity u = new UserEntity();
                u.setId(UUID.randomUUID());
                u.setUsername(username);
                u.setEmail(email);
                u.setRole(UserRole.SUPERVISOR);
                u.setBalance(BigDecimal.ZERO);
                u.setPasswordHash(hash);
                u.setCreatedAt(OffsetDateTime.now());
                return template.insert(u);
            }).doOnNext(u -> log.info("Created initial supervisor '{}'", u.getUsername()))
                    .thenReturn(true);
        });
    }
}
