package com.example.spamer.dispatch.client;

import com.example.spamer.common.feign.FeignErrorTranslator;
import java.util.UUID;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

// Runs on any failed call and on an open circuit breaker.
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    private static final String DEPENDENCY = "user-service";

    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            @Override
            public UserDto get(UUID id) {
                throw FeignErrorTranslator.translate(DEPENDENCY, cause);
            }

            @Override
            public BalanceDto debit(UUID id, DebitDto request) {
                throw FeignErrorTranslator.translate(DEPENDENCY, cause);
            }

            @Override
            public BalanceDto refund(UUID id, RefundDto request) {
                throw FeignErrorTranslator.translate(DEPENDENCY, cause);
            }
        };
    }
}
