package com.example.spamer.dispatch.dto;

import com.example.spamer.dispatch.domain.ProxyProtocol;
import com.example.spamer.dispatch.domain.ProxyStatus;
import java.util.UUID;

public record ProxyResponse(
        UUID id,
        String host,
        int port,
        ProxyProtocol protocol,
        String country,
        ProxyStatus status,
        UUID providerId) {
}
