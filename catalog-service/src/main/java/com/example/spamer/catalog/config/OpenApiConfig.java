package com.example.spamer.catalog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {


    @Bean
    public OpenAPI catalogOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Catalog Service API").version("v1")
                        .description("Catalog of dispatch services. Reactor + Spring Data JPA."))
                .servers(List.of(new Server().url("/")));
    }
}
