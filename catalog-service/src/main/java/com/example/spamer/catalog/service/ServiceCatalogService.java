package com.example.spamer.catalog.service;

import com.example.spamer.catalog.dto.CreateServiceRequest;
import com.example.spamer.catalog.dto.PatchServiceRequest;
import com.example.spamer.catalog.dto.ServiceResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive facade over blocking JPA. Each call moves to boundedElastic, so JDBC
 * never blocks a Netty event-loop thread. The transaction opens and closes
 * inside one store call, on one worker thread, because JPA binds it to the thread.
 */
@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private final ServiceCatalogStore store;

    public Mono<ServiceResponse> create(CreateServiceRequest req) {
        return blocking(() -> store.create(req));
    }

    public Mono<ServiceResponse> get(UUID id) {
        return blocking(() -> store.get(id));
    }

    public Mono<List<ServiceResponse>> list(int page, int size) {
        return blocking(() -> store.list(page, size));
    }

    public Mono<Long> count() {
        return blocking(store::count);
    }

    public Mono<ServiceResponse> replace(UUID id, CreateServiceRequest req) {
        return blocking(() -> store.replace(id, req));
    }

    public Mono<ServiceResponse> patch(UUID id, PatchServiceRequest req) {
        return blocking(() -> store.patch(id, req));
    }

    public Mono<Void> delete(UUID id) {
        return Mono.<Void>fromRunnable(() -> store.delete(id))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static <T> Mono<T> blocking(Callable<T> call) {
        return Mono.fromCallable(call).subscribeOn(Schedulers.boundedElastic());
    }
}
