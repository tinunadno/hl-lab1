package com.example.spamer.dispatch.dto;

import com.example.spamer.dispatch.domain.SpamStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record SpamSendResponse(
        UUID spamLogId,
        SpamStatus status,
        BigDecimal charged,
        BigDecimal remainingBalance,
        int delivered,
        String target,
        int lastHttpStatus) {
}
