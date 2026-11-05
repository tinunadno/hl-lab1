package com.example.spamer.user.config;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignWebFluxConfig {

    // Boot registers this bean only for servlet apps, but Feign's encoder and
    // decoder need it in a WebFlux app too.
    @Bean
    public HttpMessageConverters feignHttpMessageConverters() {
        return new HttpMessageConverters();
    }
}
