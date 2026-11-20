package com.example.spamer.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest
class AuthRouteConfigTest {

    @Autowired
    RouteLocator routes;

    @Test
    void loginGoesToUserService() {
        Route auth = routes.getRoutes()
                .filter(r -> AuthRouteConfig.AUTH_ROUTE_ID.equals(r.getId()))
                .blockFirst();

        assertThat(auth).isNotNull();
        assertThat(auth.getUri()).isEqualTo(URI.create("lb://user-service"));
        assertThat(auth.getFilters()).isNotEmpty();
    }
}
