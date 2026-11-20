package com.example.spamer.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Подключается в сервисах, которые сами выпускают токены (логин или вызовы между сервисами). */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtIssuerConfig {

    @Bean
    public JwtTokenIssuer jwtTokenIssuer(JwtProperties props) {
        return new JwtTokenIssuer(JwtSupport.encoder(props), props);
    }
}
