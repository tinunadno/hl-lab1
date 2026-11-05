package com.example.spamer.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.spamer.common.error.DependencyUnavailableException;
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

    @Test
    void writesAfterCatalogConfirmsService() {
        SubscriptionResponse saved =
                new SubscriptionResponse(userId, serviceId, Instant.now(), BigDecimal.TEN);
        when(catalog.get(serviceId))
                .thenReturn(new CatalogClient.CatalogServiceDto(serviceId, "bulk", true));
        when(writer.write(request, "bulk")).thenReturn(Mono.just(saved));

        StepVerifier.create(service.subscribe(request)).expectNext(saved).verifyComplete();
    }

    @Test
    void writesNothingWhenCatalogIsUnavailable() {
        when(catalog.get(any())).thenThrow(
                new DependencyUnavailableException("catalog-service", new RuntimeException()));

        StepVerifier.create(service.subscribe(request))
                .expectError(DependencyUnavailableException.class)
                .verify();

        verify(writer, never()).write(any(), any());
    }
}
