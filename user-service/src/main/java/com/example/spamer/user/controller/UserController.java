package com.example.spamer.user.controller;

import com.example.spamer.common.security.CurrentUser;
import com.example.spamer.user.dto.CreateUserRequest;
import com.example.spamer.user.dto.PatchUserRequest;
import com.example.spamer.user.dto.UserResponse;
import com.example.spamer.user.service.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users")
@Validated
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    /** Пользователей создают только супервайзеры. */
    @PostMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public Mono<ResponseEntity<UserResponse>> create(@Valid @RequestBody CreateUserRequest req) {
        return service.create(req).map(created -> ResponseEntity
                .created(URI.create("/api/v1/users/" + created.id()))
                .body(created));
    }

    @GetMapping("/me")
    public Mono<UserResponse> me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.me(CurrentUser.from(jwt));
    }

    @GetMapping("/{id}")
    public Mono<UserResponse> get(
            @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, CurrentUser.from(jwt));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public Mono<ResponseEntity<List<UserResponse>>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Mono.zip(service.list(page, size).collectList(), service.count())
                .map(result -> ResponseEntity.ok()
                        .header("X-Total-Count", String.valueOf(result.getT2()))
                        .body(result.getT1()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public Mono<UserResponse> replace(
            @PathVariable UUID id, @Valid @RequestBody CreateUserRequest req) {
        return service.replace(id, req);
    }

    /** Супервайзер меняет что угодно, владелец профиля только email и пароль. */
    @PatchMapping("/{id}")
    public Mono<UserResponse> patch(
            @PathVariable UUID id, @Valid @RequestBody PatchUserRequest req,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.patch(id, req, CurrentUser.from(jwt));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.delete(id, CurrentUser.from(jwt))
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
