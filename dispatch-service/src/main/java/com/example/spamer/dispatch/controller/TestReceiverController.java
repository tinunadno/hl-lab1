package com.example.spamer.dispatch.controller;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/test-receiver")
public class TestReceiverController {

    private static final Logger log = LoggerFactory.getLogger(TestReceiverController.class);

    @PostMapping("/inbox")
    public ResponseEntity<Map<String, Object>> inbox(
            @RequestBody(required = false) Map<String, Object> payload) {
        log.info("[test-receiver] got message: {}", payload);
        return ResponseEntity.ok(Map.of(
                "received", true,
                "at", Instant.now().toString(),
                "echo", payload == null ? Map.of() : payload));
    }
}
