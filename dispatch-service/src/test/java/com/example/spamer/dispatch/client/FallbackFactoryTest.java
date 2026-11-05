package com.example.spamer.dispatch.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.spamer.common.error.DependencyUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FallbackFactoryTest {

    private final RuntimeException failure = new RuntimeException("down");

    @Test
    void userClientFallbackReportsUnavailable() {
        UserClient client = new UserClientFallbackFactory().create(failure);
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> client.get(id))
                .isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> client.debit(id, null))
                .isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> client.refund(id, null))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void catalogClientFallbackReportsUnavailable() {
        CatalogClient client = new CatalogClientFallbackFactory().create(failure);

        assertThatThrownBy(() -> client.get(UUID.randomUUID()))
                .isInstanceOf(DependencyUnavailableException.class);
    }
}
