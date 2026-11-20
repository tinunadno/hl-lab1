package com.example.spamer.catalog.controller;

import com.example.spamer.catalog.dto.CreateServiceRequest;
import com.example.spamer.catalog.dto.PatchServiceRequest;
import com.example.spamer.catalog.dto.ServiceResponse;
import com.example.spamer.catalog.service.ServiceCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/services")
@Validated
@RequiredArgsConstructor
public class ServiceController {

    /** Каталог читают все вошедшие, а меняют только супервайзер и оператор закупок. */
    static final String CAN_EDIT = "hasAnyRole('SUPERVISOR', 'BUY_OPERATOR')";

    private final ServiceCatalogService service;

    @PostMapping
    @PreAuthorize(CAN_EDIT)
    public Mono<ResponseEntity<ServiceResponse>> create(@Valid @RequestBody CreateServiceRequest req) {
        return service.create(req).map(created -> ResponseEntity
                .created(URI.create("/api/v1/services/" + created.id()))
                .body(created));
    }

    @GetMapping("/{id}")
    public Mono<ServiceResponse> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    public Mono<ResponseEntity<List<ServiceResponse>>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Mono.zip(service.list(page, size), service.count())
                .map(result -> ResponseEntity.ok()
                        .header("X-Total-Count", String.valueOf(result.getT2()))
                        .body(result.getT1()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_EDIT)
    public Mono<ServiceResponse> replace(
            @PathVariable UUID id, @Valid @RequestBody CreateServiceRequest req) {
        return service.replace(id, req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(CAN_EDIT)
    public Mono<ServiceResponse> patch(
            @PathVariable UUID id, @Valid @RequestBody PatchServiceRequest req) {
        return service.patch(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_EDIT)
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id) {
        return service.delete(id).thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
