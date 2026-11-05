package com.example.spamer.dispatch.client;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "catalog-service", fallbackFactory = CatalogClientFallbackFactory.class)
public interface CatalogClient {

    @GetMapping("/api/v1/services/{id}")
    ServiceDto get(@PathVariable("id") UUID id);

    record ServiceDto(UUID id, String name, BigDecimal pricePerMessage, boolean active) {
    }
}
