package com.example.spamer.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.DependencyUnavailableException;
import com.example.spamer.common.error.ForbiddenException;
import com.example.spamer.common.security.CurrentUser;
import com.example.spamer.user.client.CatalogClient;
import com.example.spamer.user.dto.SubscriptionRequest;
import com.example.spamer.user.dto.SubscriptionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock CatalogClient catalog;
    @Mock SubscriptionWriter writer;
    @InjectMocks SubscriptionService service;

    UUID userId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();
    SubscriptionRequest request = new SubscriptionRequest(userId, serviceId, BigDecimal.TEN);
    CurrentUser supervisor = new CurrentUser(UUID.randomUUID(), "boss", "SUPERVISOR");

    @Test
    void writesAfterCatalogConfirmsService() {
        SubscriptionResponse saved =
                new SubscriptionResponse(userId, serviceId, Instant.now(), BigDecimal.TEN);
        when(catalog.get(serviceId))
                .thenReturn(new CatalogClient.CatalogServiceDto(serviceId, "bulk", true));
        when(writer.write(request, "bulk")).thenReturn(Mono.just(saved));

        StepVerifier.create(service.subscribe(request, supervisor)).expectNext(saved).verifyComplete();
    }

    @Test
    void writesNothingWhenCatalogIsUnavailable() {
        when(catalog.get(any())).thenThrow(
                new DependencyUnavailableException("catalog-service", new RuntimeException()));

        StepVerifier.create(service.subscribe(request, supervisor))
                .expectError(DependencyUnavailableException.class)
                .verify();

        verify(writer, never()).write(any(), any());
    }

    @Test
    void forbiddenRequestNeverReachesCatalog() {
        CurrentUser stranger = new CurrentUser(UUID.randomUUID(), "x", "USER_FREE");

        StepVerifier.create(service.subscribe(request, stranger))
                .expectError(ForbiddenException.class)
                .verify();

        verify(catalog, never()).get(any());
    }

    @Test
    void clientIsSubscribedAsThemselvesWithoutDiscount() {
        CurrentUser client = new CurrentUser(userId, "me", "USER_PRO");

        SubscriptionRequest effective = SubscriptionService.authorize(
                new SubscriptionRequest(null, serviceId, null), client);

        assertThat(effective.userId()).isEqualTo(userId);
        assertThat(effective.discountPercent()).isEqualByComparingTo("0");
    }

    @Test
    void clientCannotGrantDiscountOrSubscribeOthers() {
        CurrentUser client = new CurrentUser(userId, "me", "USER_FREE");

        assertThatThrownBy(() -> SubscriptionService.authorize(request, client))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> SubscriptionService.authorize(
                new SubscriptionRequest(UUID.randomUUID(), serviceId, null), client))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> SubscriptionService.authorize(
                new SubscriptionRequest(null, serviceId, null),
                new CurrentUser(null, "dispatch-service", "SERVICE")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void supervisorMustNameUserAndKeepsDiscount() {
        assertThat(SubscriptionService.authorize(request, supervisor)).isSameAs(request);
        assertThatThrownBy(() -> SubscriptionService.authorize(
                new SubscriptionRequest(null, serviceId, null), supervisor))
                .isInstanceOf(BusinessException.class);
    }
}
