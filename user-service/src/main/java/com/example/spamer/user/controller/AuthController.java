package com.example.spamer.user.controller;

import com.example.spamer.user.dto.LoginRequest;
import com.example.spamer.user.dto.TokenResponse;
import com.example.spamer.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Получить JWT по имени и паролю")
    public Mono<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }
}
