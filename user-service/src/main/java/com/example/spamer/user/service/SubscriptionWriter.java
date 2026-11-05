package com.example.spamer.user.service;

import com.example.spamer.common.error.ConflictException;
import com.example.spamer.user.dto.SubscriptionRequest;
import com.example.spamer.user.dto.SubscriptionResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Transactional part of a subscription. Creating the subscription row and
 * flipping the user to USER_PRO have to be consistent - otherwise you get "PRO
 * without a subscription" or "subscription but still free". Both tables belong
 * to this service, so one local transaction covers them.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionWriter {

    private final DatabaseClient db;
    private final UserService users;

    @Transactional
    public Mono<SubscriptionResponse> write(SubscriptionRequest req, String serviceName) {
        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal discount = req.discountPercent() != null ? req.discountPercent() : BigDecimal.ZERO;
        return users.find(req.userId())
                .then(db.sql("""
                                INSERT INTO user_service_subscriptions
                                    (user_id, service_id, subscribed_at, discount_percent)
                                VALUES (:userId, :serviceId, :subscribedAt, :discount)
                                ON CONFLICT (user_id, service_id) DO NOTHING
                                """)
                        .bind("userId", req.userId())
                        .bind("serviceId", req.serviceId())
                        .bind("subscribedAt", now)
                        .bind("discount", discount)
                        .fetch().rowsUpdated())
                .flatMap(rows -> rows > 0
                        ? Mono.just(rows)
                        : Mono.<Long>error(new ConflictException(
                                "Already subscribed to service: " + serviceName)))
                // First paid subscription promotes a free user. Admins/operators keep their role.
                .then(db.sql("""
                                UPDATE users SET role = 'USER_PRO', updated_at = now()
                                WHERE id = :userId AND role = 'USER_FREE'
                                """)
                        .bind("userId", req.userId())
                        .fetch().rowsUpdated())
                .thenReturn(new SubscriptionResponse(
                        req.userId(), req.serviceId(), now.toInstant(), discount));
    }
}
