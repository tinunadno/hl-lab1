package com.example.spamer.common.feign;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.ConflictException;
import com.example.spamer.common.error.DependencyUnavailableException;
import com.example.spamer.common.error.NotFoundException;
import feign.FeignException;

/**
 * Turns whatever reached a Feign fallback into a domain exception. A 4xx is a
 * real answer from the remote service and keeps its meaning; everything else
 * (connection refused, timeout, 5xx, open breaker) means the dependency is
 * unavailable.
 */
public final class FeignErrorTranslator {

    private FeignErrorTranslator() {
    }

    public static RuntimeException translate(String dependency, Throwable failure) {
        FeignException feign = findFeignException(failure);
        if (feign != null) {
            String message = dependency + " rejected the request: " + feign.contentUTF8();
            switch (feign.status()) {
                case 404:
                    return new NotFoundException(message);
                case 409:
                    return new ConflictException(message);
                case 400:
                case 422:
                    return new BusinessException(message);
                default:
                    break;
            }
        }
        return new DependencyUnavailableException(dependency, failure);
    }

    // The breaker's time limiter may wrap the original exception.
    private static FeignException findFeignException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof FeignException feign) {
                return feign;
            }
            current = current.getCause();
        }
        return null;
    }
}
