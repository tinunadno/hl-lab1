package com.example.spamer.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.cloud.config.server.git.basedir=target/config-repo-clone"
})
class ConfigServerApplicationTest {

    @Autowired
    EnvironmentRepository environments;

    @Test
    void eachServiceIsReadFromItsDirectory() {
        assertThat(properties("user-service"))
                .containsEntry("server.port", 8081)
                .containsEntry("spamer.circuit-breaker.sliding-window-size", 10);
        assertThat(properties("catalog-service")).containsEntry("server.port", 8082);
        assertThat(properties("dispatch-service"))
                .containsEntry("server.port", 8083);
        assertThat(properties("dispatch-service").get("spamer.sender.target-url").toString())
                .contains("test-receiver/inbox");
        assertThat(properties("api-gateway"))
                .containsEntry("spring.cloud.gateway.server.webflux.routes[0].id", "user-service")
                .containsEntry("spamer.gateway.timeout-seconds", 60);
    }

    private Map<String, Object> properties(String application) {
        Environment environment = environments.findOne(application, "default", "main");
        Map<String, Object> merged = new LinkedHashMap<>();
        for (PropertySource source : environment.getPropertySources()) {
            source.getSource().forEach((key, value) -> merged.putIfAbsent(String.valueOf(key), value));
        }
        return merged;
    }
}
