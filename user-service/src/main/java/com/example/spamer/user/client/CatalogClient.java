package com.example.spamer.user.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "catalog-service", fallbackFactory = CatalogClientFallbackFactory.class)
public interface CatalogClient {

    @GetMapping("/api/v1/services/{id}")
    CatalogServiceDto get(@PathVariable("id") UUID id);

    record CatalogServiceDto(UUID id, String name, boolean active) {
    }
}
