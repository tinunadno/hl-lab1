package com.example.spamer.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeignWebFluxConfigTest {

    @Test
    void exposesConvertersForFeign() {
        assertThat(new FeignWebFluxConfig().feignHttpMessageConverters().getConverters()).isNotNull();
    }
}
