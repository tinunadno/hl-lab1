package com.example.spamer.user.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.spamer.common.error.DependencyUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogClientFallbackFactoryTest {

    @Test
    void reportsCatalogUnavailable() {
        CatalogClient client = new CatalogClientFallbackFactory()
                .create(new RuntimeException("down"));

        assertThatThrownBy(() -> client.get(UUID.randomUUID()))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("catalog-service");
    }
}
