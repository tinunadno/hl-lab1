package com.example.spamer.dispatch.client;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

    @GetMapping("/api/v1/users/{id}")
    UserDto get(@PathVariable("id") UUID id);

    @PostMapping("/internal/v1/users/{id}/balance/debit")
    BalanceDto debit(@PathVariable("id") UUID id, @RequestBody DebitDto request);

    @PostMapping("/internal/v1/users/{id}/balance/refund")
    BalanceDto refund(@PathVariable("id") UUID id, @RequestBody RefundDto request);

    record UserDto(UUID id, BigDecimal balance) {
    }

    record BalanceDto(BigDecimal balance) {
    }

    record DebitDto(UUID operationId, BigDecimal amount) {
    }

    record RefundDto(UUID operationId) {
    }
}
