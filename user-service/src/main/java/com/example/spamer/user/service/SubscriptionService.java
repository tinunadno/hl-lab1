package com.example.spamer.user.service;

import com.example.spamer.user.client.CatalogClient;
import com.example.spamer.user.dto.SubscriptionRequest;
import com.example.spamer.user.dto.SubscriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final CatalogClient catalog;
    private final SubscriptionWriter writer;

    public Mono<SubscriptionResponse> subscribe(SubscriptionRequest req) {


        return Mono.fromCallable(() -> catalog.get(req.serviceId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(service -> writer.write(req, service.name()));
    }
}
