package com.example.spamer.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void describesCatalogApi() {
        var api = new OpenApiConfig().catalogOpenApi();

        assertThat(api.getInfo().getTitle()).isEqualTo("Catalog Service API");
        assertThat(api.getServers()).singleElement().extracting(server -> server.getUrl()).isEqualTo("/");
    }
}
