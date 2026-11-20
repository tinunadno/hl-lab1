package com.example.spamer.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code userId} обязателен только супервайзеру. Клиент подписывает себя: поле можно не
 * передавать, а чужой id даёт 403. Скидку назначает только супервайзер.
 */
public record SubscriptionRequest(
        UUID userId,
        @NotNull UUID serviceId,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal discountPercent) {
}
