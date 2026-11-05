package com.example.spamer.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RefundRequest(@NotNull UUID operationId) {
}
