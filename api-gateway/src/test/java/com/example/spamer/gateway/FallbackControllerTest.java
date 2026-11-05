package com.example.spamer.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FallbackControllerTest {

    @Test
    void answersWithReadable503() {
        var response = new FallbackController().fallback("user-service");

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().message()).contains("user-service");
    }
}
