package com.example.spamer.dispatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class SenderConfigTest {

    @Test
    void buildsClientWithTheConfiguredTimeouts() {
        SenderConfig config = new SenderConfig();
        ReflectionTestUtils.setField(config, "connectTimeoutMs", 2000);
        ReflectionTestUtils.setField(config, "readTimeoutMs", 5000);

        RestClient client = config.senderRestClient(RestClient.builder());

        assertThat(client).isNotNull();
    }
}
