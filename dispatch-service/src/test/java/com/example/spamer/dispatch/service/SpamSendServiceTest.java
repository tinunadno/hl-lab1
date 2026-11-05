package com.example.spamer.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.DependencyUnavailableException;
import com.example.spamer.common.error.NotFoundException;
import com.example.spamer.dispatch.client.CatalogClient;
import com.example.spamer.dispatch.client.UserClient;
import com.example.spamer.dispatch.domain.SpamStatus;
import com.example.spamer.dispatch.dto.SpamSendRequest;
import com.example.spamer.dispatch.dto.SpamSendResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpamSendServiceTest {

    @Mock CatalogClient catalog;
    @Mock UserClient users;
    @Mock SpamLogService logs;
    @Mock RefundService refunds;
    @Mock MessageSender sender;

    SpamSendService service;

    UUID userId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    UUID logId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SpamSendService(catalog, users, logs, refunds, sender);
    }

    private void catalogItem(String price, boolean active) {
        when(catalog.get(serviceId)).thenReturn(
                new CatalogClient.ServiceDto(serviceId, "svc", new BigDecimal(price), active));
    }

    private SpamSendRequest request(int count) {
        return new SpamSendRequest(userId, serviceId, "t@example.com", "hi", count);
    }

    @Test
    void chargesAndMarksSent() {
        catalogItem("2.00", true);
        when(logs.createQueued(any(), any())).thenReturn(logId);
        when(users.debit(eq(userId), any()))
                .thenReturn(new UserClient.BalanceDto(new BigDecimal("96.00")));
        when(sender.send(any(), any(), anyInt()))
                .thenReturn(new MessageSender.SendResult(200, true, "{}", 3));

        SpamSendResponse res = service.send(request(2));

        assertThat(res.status()).isEqualTo(SpamStatus.SENT);
        assertThat(res.charged()).isEqualByComparingTo("4.00");
        assertThat(res.remainingBalance()).isEqualByComparingTo("96.00");
        assertThat(res.delivered()).isEqualTo(2);
        verify(sender, times(2)).send(any(), any(), anyInt());
        verify(logs).markSent(logId);
        verify(refunds, never()).attempt(any());
    }

    @Test
    void refundsWhenReceiverRejects() {
        catalogItem("2.00", true);
        when(logs.createQueued(any(), any())).thenReturn(logId);
        when(users.debit(eq(userId), any()))
                .thenReturn(new UserClient.BalanceDto(new BigDecimal("98.00")));
        when(sender.send(any(), any(), anyInt()))
                .thenReturn(new MessageSender.SendResult(503, false, "down", 1));

        assertThatThrownBy(() -> service.send(request(1)))
                .isInstanceOf(BusinessException.class);

        verify(logs).markFailedWithRefundPending(logId);
        verify(refunds).attempt(any());
        verify(logs, never()).markSent(any());
    }

    @Test
    void discardsJournalWhenBalanceTooLow() {
        catalogItem("5.00", true);
        when(logs.createQueued(any(), any())).thenReturn(logId);
        when(users.debit(eq(userId), any())).thenThrow(new BusinessException("Insufficient"));

        assertThatThrownBy(() -> service.send(request(1)))
                .isInstanceOf(BusinessException.class);

        verify(logs).discard(logId);
        verify(refunds, never()).attempt(any());
        verify(sender, never()).send(any(), any(), anyInt());
    }

    @Test
    void compensatesWhenDebitOutcomeIsUnknown() {
        catalogItem("1.00", true);
        when(logs.createQueued(any(), any())).thenReturn(logId);
        when(users.debit(eq(userId), any())).thenThrow(
                new DependencyUnavailableException("user-service", new RuntimeException()));

        assertThatThrownBy(() -> service.send(request(1)))
                .isInstanceOf(DependencyUnavailableException.class);

        verify(logs).markFailedWithRefundPending(logId);
        verify(refunds).attempt(any());
        verify(sender, never()).send(any(), any(), anyInt());
    }

    @Test
    void rejectsInactiveServiceBeforeAnyWrite() {
        catalogItem("1.00", false);

        assertThatThrownBy(() -> service.send(request(1)))
                .isInstanceOf(BusinessException.class);

        verify(logs, never()).createQueued(any(), any());
    }

    @Test
    void missingServiceGives404() {
        when(catalog.get(serviceId)).thenThrow(NotFoundException.of("Service", serviceId));

        assertThatThrownBy(() -> service.send(request(1)))
                .isInstanceOf(NotFoundException.class);
    }
}
