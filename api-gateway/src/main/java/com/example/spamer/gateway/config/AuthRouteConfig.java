package com.example.spamer.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Маршрут логина в user-service. Задан в коде, а не в config-repo, чтобы вход работал при любой
 * версии репозитория конфигурации; маршруты из config-repo продолжают действовать рядом с ним.
 * Заголовок {@code Authorization} gateway передаёт сервисам без изменений, токен проверяет
 * каждый сервис сам.
 */
@Configuration
public class AuthRouteConfig {

    public static final String AUTH_ROUTE_ID = "user-service-auth";

    @Bean
    public RouteLocator authRoute(RouteLocatorBuilder routes) {
        return routes.routes()
                .route(AUTH_ROUTE_ID, r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f.circuitBreaker(cb -> cb
                                .setName("user-service")
                                .setFallbackUri("forward:/fallback/user-service")))
                        .uri("lb://user-service"))
                .build();
    }
}
