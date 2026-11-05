package com.example.spamer.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record DebitRequest(@NotNull UUID operationId, @NotNull @Positive BigDecimal amount) {
}
