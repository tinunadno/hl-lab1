package com.example.spamer.dispatch.service;

import com.example.spamer.common.error.NotFoundException;
import com.example.spamer.dispatch.client.UserClient;
import com.example.spamer.dispatch.service.SpamLogService.PendingRefund;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Compensation step of the /spam/send saga. If user-service is down the refund
 * stays pending in the spam log and is retried on a schedule. Retrying is safe
 * because refunds are idempotent by operationId.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final UserClient users;
    private final SpamLogService logs;

    public void attempt(PendingRefund refund) {
        try {
            users.refund(refund.userId(), new UserClient.RefundDto(refund.operationId()));
            logs.clearRefundPending(refund.spamLogId());
        } catch (NotFoundException e) {
            // The user was deleted, so there is nobody left to refund.
            log.warn("Refund {} dropped: user {} no longer exists",
                    refund.operationId(), refund.userId());
            logs.clearRefundPending(refund.spamLogId());
        } catch (RuntimeException e) {
            log.warn("Refund {} still pending: {}", refund.operationId(), e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${spamer.refund.retry-delay-ms:30000}")
    public void retryPending() {
        logs.findPendingRefunds().forEach(this::attempt);
    }
}
