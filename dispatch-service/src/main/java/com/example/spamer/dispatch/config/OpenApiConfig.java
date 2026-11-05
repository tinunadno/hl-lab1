package com.example.spamer.dispatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    // Relative server: "Try it out" in the shared Swagger goes through the gateway.
    @Bean
    public OpenAPI dispatchOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Dispatch Service API").version("v1")
                        .description("Proxies, spam log and sending to the configured test "
                                + "receiver. Spring MVC + JPA + Feign."))
                .servers(List.of(new Server().url("/")));
    }
}
