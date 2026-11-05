package com.example.spamer.user.service;

import com.example.spamer.common.error.ConflictException;
import com.example.spamer.common.error.NotFoundException;
import com.example.spamer.user.domain.UserEntity;
import com.example.spamer.user.domain.UserRepository;
import com.example.spamer.user.dto.CreateUserRequest;
import com.example.spamer.user.dto.PatchUserRequest;
import com.example.spamer.user.dto.UserResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repo;
    private final R2dbcEntityTemplate template;

    @Transactional
    public Mono<UserResponse> create(CreateUserRequest req) {
        return ensureUsernameFree(req.username())
                .then(ensureEmailFree(req.email()))
                .then(Mono.defer(() -> {
                    UserEntity u = new UserEntity();
                    u.setId(UUID.randomUUID());
                    u.setCreatedAt(OffsetDateTime.now());
                    apply(u, req);
                    // The id is assigned here, so save() would try an UPDATE.
                    return template.insert(u);
                }))
                .map(UserResponse::from);
    }

    public Mono<UserResponse> get(UUID id) {
        return find(id).map(UserResponse::from);
    }

    public Flux<UserResponse> list(int page, int size) {
        return repo.findAllBy(PageRequest.of(page, size, Sort.by("id")))
                .map(UserResponse::from);
    }

    public Mono<Long> count() {
        return repo.count();
    }

    @Transactional
    public Mono<UserResponse> replace(UUID id, CreateUserRequest req) {
        return find(id).flatMap(u -> {
            Mono<Void> usernameCheck = u.getUsername().equals(req.username())
                    ? Mono.empty() : ensureUsernameFree(req.username());
            Mono<Void> emailCheck = u.getEmail().equals(req.email())
                    ? Mono.empty() : ensureEmailFree(req.email());
            return usernameCheck.then(emailCheck).then(Mono.defer(() -> {
                apply(u, req);
                return saveUpdated(u);
            }));
        }).map(UserResponse::from);
    }

    @Transactional
    public Mono<UserResponse> patch(UUID id, PatchUserRequest req) {
        return find(id).flatMap(u -> {
            boolean newUsername = req.username() != null && !req.username().equals(u.getUsername());
            boolean newEmail = req.email() != null && !req.email().equals(u.getEmail());
            Mono<Void> usernameCheck = newUsername ? ensureUsernameFree(req.username()) : Mono.empty();
            Mono<Void> emailCheck = newEmail ? ensureEmailFree(req.email()) : Mono.empty();
            return usernameCheck.then(emailCheck).then(Mono.defer(() -> {
                if (req.username() != null) {
                    u.setUsername(req.username());
                }
                if (req.email() != null) {
                    u.setEmail(req.email());
                }
                if (req.role() != null) {
                    u.setRole(req.role());
                }
                if (req.balance() != null) {
                    u.setBalance(req.balance());
                }
                return saveUpdated(u);
            }));
        }).map(UserResponse::from);
    }

    @Transactional
    public Mono<Void> delete(UUID id) {
        return find(id).flatMap(repo::delete);
    }

    public Mono<UserEntity> find(UUID id) {
        return repo.findById(id)
                .switchIfEmpty(Mono.error(() -> NotFoundException.of("User", id)));
    }

    private Mono<UserEntity> saveUpdated(UserEntity u) {
        u.setUpdatedAt(OffsetDateTime.now());
        return repo.save(u);
    }

    private void apply(UserEntity u, CreateUserRequest req) {
        u.setUsername(req.username());
        u.setEmail(req.email());
        u.setRole(req.role());
        u.setBalance(req.balance() != null ? req.balance() : BigDecimal.ZERO);
    }

    private Mono<Void> ensureUsernameFree(String username) {
        return repo.existsByUsername(username).flatMap(taken -> taken
                ? Mono.<Void>error(new ConflictException("Username already taken: " + username))
                : Mono.<Void>empty());
    }

    private Mono<Void> ensureEmailFree(String email) {
        return repo.existsByEmail(email).flatMap(taken -> taken
                ? Mono.<Void>error(new ConflictException("Email already registered: " + email))
                : Mono.<Void>empty());
    }
}
