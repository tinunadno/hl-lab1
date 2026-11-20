package com.example.spamer.user;

import com.example.spamer.common.feign.FeignCircuitBreakerConfig;
import com.example.spamer.common.feign.ServiceAuthFeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableFeignClients
@Import({FeignCircuitBreakerConfig.class, ServiceAuthFeignConfig.class})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
