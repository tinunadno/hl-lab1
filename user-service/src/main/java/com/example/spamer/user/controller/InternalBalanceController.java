package com.example.spamer.user.controller;

import com.example.spamer.user.dto.BalanceResponse;
import com.example.spamer.user.dto.DebitRequest;
import com.example.spamer.user.dto.RefundRequest;
import com.example.spamer.user.service.BalanceService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;


@Hidden
@RestController
@RequestMapping("/internal/v1/users/{id}/balance")
@RequiredArgsConstructor
public class InternalBalanceController {

    private final BalanceService balances;

    @PostMapping("/debit")
    public Mono<BalanceResponse> debit(
            @PathVariable UUID id, @Valid @RequestBody DebitRequest req) {
        return balances.debit(id, req.operationId(), req.amount());
    }

    @PostMapping("/refund")
    public Mono<BalanceResponse> refund(
            @PathVariable UUID id, @Valid @RequestBody RefundRequest req) {
        return balances.refund(id, req.operationId());
    }
}
