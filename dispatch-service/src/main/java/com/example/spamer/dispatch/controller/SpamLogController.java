package com.example.spamer.dispatch.controller;

import com.example.spamer.common.security.CurrentUser;
import com.example.spamer.dispatch.dto.CreateSpamLogRequest;
import com.example.spamer.dispatch.dto.PatchSpamLogRequest;
import com.example.spamer.dispatch.dto.CursorPage;
import com.example.spamer.dispatch.dto.SpamLogResponse;
import com.example.spamer.dispatch.security.DispatchAccessPolicy;
import com.example.spamer.dispatch.service.SpamLogService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

@RestController
@RequestMapping("/api/v1/spam-log")
@Validated
@RequiredArgsConstructor
public class SpamLogController {

    /** Журнал читают супервайзер (весь) и клиенты (только свои записи). */
    static final String CAN_READ = "hasAnyRole('SUPERVISOR', 'USER_PRO', 'USER_FREE')";
    /** Ручное редактирование журнала только у супервайзера. */
    static final String CAN_EDIT = "hasRole('SUPERVISOR')";

    private final SpamLogService service;
    private final DispatchAccessPolicy policy;

    @PostMapping
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<SpamLogResponse> create(@Valid @RequestBody CreateSpamLogRequest req) {
        SpamLogResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/spam-log/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<SpamLogResponse> get(
            @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        SpamLogResponse found = service.get(id);
        policy.checkCanRead(found.userId(), CurrentUser.from(jwt));
        return ResponseEntity.ok(found);
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<List<SpamLogResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        UUID owner = policy.visibleOwner(CurrentUser.from(jwt));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(service.count(owner)))
                .body(service.list(page, size, owner));
    }


    @GetMapping("/feed")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<CursorPage<SpamLogResponse>> feed(
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.feed(cursor, size, policy.visibleOwner(CurrentUser.from(jwt))));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<SpamLogResponse> replace(
            @PathVariable UUID id, @Valid @RequestBody CreateSpamLogRequest req) {
        return ResponseEntity.ok(service.replace(id, req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<SpamLogResponse> patch(
            @PathVariable UUID id, @Valid @RequestBody PatchSpamLogRequest req) {
        return ResponseEntity.ok(service.patch(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
