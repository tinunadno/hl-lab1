package com.example.spamer.common.feign;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.ConflictException;
import com.example.spamer.common.error.DependencyUnavailableException;
import com.example.spamer.common.error.NotFoundException;
import feign.FeignException;


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
