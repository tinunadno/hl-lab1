package com.example.spamer.user.service;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.ForbiddenException;
import com.example.spamer.common.security.CurrentUser;
import com.example.spamer.user.client.CatalogClient;
import com.example.spamer.user.dto.SubscriptionRequest;
import com.example.spamer.user.dto.SubscriptionResponse;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final CatalogClient catalog;
    private final SubscriptionWriter writer;

    /**
     * Супервайзер подписывает любого пользователя и может дать скидку. Клиент подписывает
     * только себя и без скидки.
     */
    public Mono<SubscriptionResponse> subscribe(SubscriptionRequest req, CurrentUser actor) {
        return Mono.fromCallable(() -> authorize(req, actor))
                .flatMap(effective -> Mono.fromCallable(() -> catalog.get(effective.serviceId()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(service -> writer.write(effective, service.name())));
    }

    static SubscriptionRequest authorize(SubscriptionRequest req, CurrentUser actor) {
        if (actor.isSupervisor()) {
            if (req.userId() == null) {
                throw new BusinessException("userId is required when a supervisor subscribes a user");
            }
            return req;
        }
        UUID self = actor.id();
        if (self == null || (req.userId() != null && !req.userId().equals(self))) {
            throw new ForbiddenException("You can only subscribe yourself");
        }
        if (req.discountPercent() != null && req.discountPercent().signum() != 0) {
            throw new ForbiddenException("Only a supervisor can grant a discount");
        }
        return new SubscriptionRequest(self, req.serviceId(), BigDecimal.ZERO);
    }
}
