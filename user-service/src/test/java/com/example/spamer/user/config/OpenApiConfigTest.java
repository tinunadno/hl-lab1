package com.example.spamer.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void describesUserApi() {
        var api = new OpenApiConfig().userOpenApi();

        assertThat(api.getInfo().getTitle()).isEqualTo("User Service API");
        assertThat(api.getServers()).singleElement().extracting(server -> server.getUrl()).isEqualTo("/");
    }
}
