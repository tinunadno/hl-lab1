package com.example.spamer.common.feign;

import com.example.spamer.common.security.JwtIssuerConfig;
import com.example.spamer.common.security.JwtTokenIssuer;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Глобальный для всех Feign-клиентов сервиса перехватчик, добавляющий служебный токен. */
@Configuration
@Import(JwtIssuerConfig.class)
public class ServiceAuthFeignConfig {

    @Bean
    public RequestInterceptor serviceTokenInterceptor(
            JwtTokenIssuer issuer, @Value("${spring.application.name}") String serviceName) {
        return new ServiceTokenInterceptor(issuer, serviceName);
    }
}
