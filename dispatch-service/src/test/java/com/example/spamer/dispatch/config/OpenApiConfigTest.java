package com.example.spamer.dispatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void describesDispatchApi() {
        var api = new OpenApiConfig().dispatchOpenApi();

        assertThat(api.getInfo().getTitle()).isEqualTo("Dispatch Service API");
        assertThat(api.getServers()).singleElement().extracting(server -> server.getUrl()).isEqualTo("/");
    }
}
