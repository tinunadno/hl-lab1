package com.example.spamer.user.controller;

import com.example.spamer.user.dto.SubscriptionRequest;
import com.example.spamer.user.dto.SubscriptionResponse;
import com.example.spamer.user.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SubscriptionResponse> subscribe(@Valid @RequestBody SubscriptionRequest req) {
        return service.subscribe(req);
    }
}
