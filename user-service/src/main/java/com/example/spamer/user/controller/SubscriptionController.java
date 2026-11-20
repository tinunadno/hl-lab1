package com.example.spamer.user.controller;

import com.example.spamer.common.security.CurrentUser;
import com.example.spamer.user.dto.SubscriptionRequest;
import com.example.spamer.user.dto.SubscriptionResponse;
import com.example.spamer.user.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService service;

    /** Оператор закупок и получатель подписок не оформляют. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'USER_FREE', 'USER_PRO')")
    public Mono<SubscriptionResponse> subscribe(
            @Valid @RequestBody SubscriptionRequest req,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.subscribe(req, CurrentUser.from(jwt));
    }
}
