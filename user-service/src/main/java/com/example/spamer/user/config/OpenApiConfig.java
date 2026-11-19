package com.example.spamer.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {


    @Bean
    public OpenAPI userOpenApi() {
        return new OpenAPI()
                .info(new Info().title("User Service API").version("v1")
                        .description("Users, balances and subscriptions. Reactor + R2DBC."))
                .servers(List.of(new Server().url("/")));
    }
}
