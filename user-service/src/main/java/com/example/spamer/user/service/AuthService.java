package com.example.spamer.user.service;

import com.example.spamer.common.security.JwtTokenIssuer;
import com.example.spamer.common.security.JwtTokenIssuer.IssuedToken;
import com.example.spamer.user.domain.UserEntity;
import com.example.spamer.user.domain.UserRepository;
import com.example.spamer.user.dto.LoginRequest;
import com.example.spamer.user.dto.TokenResponse;
import com.example.spamer.user.security.PasswordHasher;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Проверяет пароль по BCrypt-хэшу и выдаёт JWT. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordHasher passwords;
    private final JwtTokenIssuer tokens;

    public Mono<TokenResponse> login(LoginRequest req) {
        return users.findByUsername(req.username())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                // пароль проверяется и для несуществующего имени, чтобы время ответа не выдавало,
                // какие имена заняты
                .flatMap(user -> passwords.matches(req.password(),
                                user.map(UserEntity::getPasswordHash).orElse(null))
                        .flatMap(ok -> ok && user.isPresent()
                                ? Mono.just(issue(user.get()))
                                : Mono.<TokenResponse>error(new InvalidCredentialsException())));
    }

    private TokenResponse issue(UserEntity user) {
        String role = user.getRole().name();
        IssuedToken token = tokens.issueForUser(user.getId(), user.getUsername(), role);
        return new TokenResponse(token.value(), "Bearer", token.expiresAt(), user.getId(), role);
    }
}
