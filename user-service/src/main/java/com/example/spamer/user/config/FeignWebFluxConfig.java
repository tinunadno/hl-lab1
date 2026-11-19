package com.example.spamer.user.config;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignWebFluxConfig {


    @Bean
    public HttpMessageConverters feignHttpMessageConverters() {
        return new HttpMessageConverters();
    }
}
