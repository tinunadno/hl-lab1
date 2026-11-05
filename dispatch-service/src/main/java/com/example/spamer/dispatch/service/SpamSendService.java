package com.example.spamer.dispatch.service;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.DependencyUnavailableException;
import com.example.spamer.dispatch.client.CatalogClient;
import com.example.spamer.dispatch.client.UserClient;
import com.example.spamer.dispatch.domain.SpamStatus;
import com.example.spamer.dispatch.dto.SpamSendRequest;
import com.example.spamer.dispatch.dto.SpamSendResponse;
import com.example.spamer.dispatch.service.SpamLogService.PendingRefund;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Saga behind POST /spam/send. The balance now lives in user-service, so the
 * single database transaction of the monolith is no longer possible. Instead:
 * read price -> journal QUEUED -> debit -> POST messages -> mark SENT.
 * If delivery fails, the send is marked FAILED and the debit is compensated.
 *
 * No step holds a database transaction during a network call.
 *
 * The destination is operator config only (spamer.sender.target-url) - the
 * request body never chooses where this connects, so it can only reach the one
 * test receiver you point it at.
 *
 * A POST that was already accepted can't be "unsent": if message 2 of N fails,
 * the money is returned, but the receiver has still got message 1.
 */
@Service
@RequiredArgsConstructor
public class SpamSendService {

    private final CatalogClient catalog;
    private final UserClient users;
    private final SpamLogService logs;
    private final RefundService refunds;
    private final MessageSender sender;

    @Value("${spamer.sender.target-url}")
    private String target;

    public SpamSendResponse send(SpamSendRequest req) {
        CatalogClient.ServiceDto service = catalog.get(req.serviceId());
        if (!service.active()) {
            throw new BusinessException("Service is not active: " + service.name());
        }
        BigDecimal cost = service.pricePerMessage()
                .multiply(BigDecimal.valueOf(req.messageCount()));

        UUID operationId = UUID.randomUUID();
        UUID logId = logs.createQueued(req, operationId);
        PendingRefund refund = new PendingRefund(logId, req.userId(), operationId);

        BigDecimal remaining = debit(req.userId(), cost, refund);

        int delivered = 0;
        int lastStatus = 0;
        for (int i = 1; i <= req.messageCount(); i++) {
            MessageSender.SendResult result =
                    sender.send(req.victimContact(), req.messageBody(), i);
            lastStatus = result.httpStatus();
            if (!result.ok()) {
                compensate(refund);
                throw new BusinessException("Receiver rejected message " + i
                        + ": HTTP " + result.httpStatus()
                        + (result.responseSnippet() != null
                            ? " " + result.responseSnippet() : ""));
            }
            delivered++;
        }

        logs.markSent(logId);
        return new SpamSendResponse(
                logId, SpamStatus.SENT, cost, remaining, delivered, target, lastStatus);
    }

    private BigDecimal debit(UUID userId, BigDecimal cost, PendingRefund refund) {
        try {
            return users.debit(userId, new UserClient.DebitDto(refund.operationId(), cost))
                    .balance();
        } catch (DependencyUnavailableException e) {
            // A timeout doesn't say whether the debit was applied, so compensate
            // anyway. Refunding an operation that never happened changes nothing.
            compensate(refund);
            throw e;
        } catch (RuntimeException e) {
            // Definite refusal (no such user, not enough money): nothing happened.
            logs.discard(refund.spamLogId());
            throw e;
        }
    }

    private void compensate(PendingRefund refund) {
        logs.markFailedWithRefundPending(refund.spamLogId());
        refunds.attempt(refund);
    }
}
