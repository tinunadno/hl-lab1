package com.example.spamer.gateway;

import com.example.spamer.common.error.ApiError;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Answer of the gateway when a service behind it is down or its breaker is open.
@Hidden
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<ApiError> fallback(@PathVariable String service) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "Service temporarily unavailable: " + service,
                "/fallback/" + service,
                null));
    }
}
