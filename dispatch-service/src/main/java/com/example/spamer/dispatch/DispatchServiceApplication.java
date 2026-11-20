package com.example.spamer.dispatch;

import com.example.spamer.common.feign.FeignCircuitBreakerConfig;
import com.example.spamer.common.feign.ServiceAuthFeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@Import({FeignCircuitBreakerConfig.class, ServiceAuthFeignConfig.class})
public class DispatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchServiceApplication.class, args);
    }
}
