package com.example.spamer.dispatch.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.spamer.common.error.DependencyUnavailableException;
import com.example.spamer.common.error.NotFoundException;
import com.example.spamer.dispatch.client.UserClient;
import com.example.spamer.dispatch.service.SpamLogService.PendingRefund;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock UserClient users;
    @Mock SpamLogService logs;
    @InjectMocks RefundService refunds;

    PendingRefund pending = new PendingRefund(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test
    void successClearsPendingFlag() {
        refunds.attempt(pending);

        verify(users).refund(eq(pending.userId()), any());
        verify(logs).clearRefundPending(pending.spamLogId());
    }

    @Test
    void unavailableUserServiceKeepsItPending() {
        when(users.refund(any(), any())).thenThrow(
                new DependencyUnavailableException("user-service", new RuntimeException()));

        refunds.attempt(pending);

        verify(logs, never()).clearRefundPending(any());
    }

    @Test
    void deletedUserClearsPendingFlag() {
        when(users.refund(any(), any())).thenThrow(NotFoundException.of("User", pending.userId()));

        refunds.attempt(pending);

        verify(logs).clearRefundPending(pending.spamLogId());
    }

    @Test
    void scheduledRetryProcessesPending() {
        when(logs.findPendingRefunds()).thenReturn(List.of(pending));

        refunds.retryPending();

        verify(users).refund(eq(pending.userId()), any());
    }
}
