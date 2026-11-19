package com.example.spamer.user.client;

import com.example.spamer.common.feign.FeignErrorTranslator;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;


@Component
public class CatalogClientFallbackFactory implements FallbackFactory<CatalogClient> {

    @Override
    public CatalogClient create(Throwable cause) {
        return id -> {
            throw FeignErrorTranslator.translate("catalog-service", cause);
        };
    }
}
