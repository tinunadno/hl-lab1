package com.example.spamer.dispatch.controller;

import com.example.spamer.common.security.CurrentUser;
import com.example.spamer.dispatch.dto.SpamSendRequest;
import com.example.spamer.dispatch.dto.SpamSendResponse;
import com.example.spamer.dispatch.security.DispatchAccessPolicy;
import com.example.spamer.dispatch.service.SpamSendService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spam")
@RequiredArgsConstructor
public class SpamController {

    private final SpamSendService service;
    private final DispatchAccessPolicy policy;

    /** Оператор закупок и получатель рассылки не делают. */
    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'USER_PRO', 'USER_FREE')")
    public ResponseEntity<SpamSendResponse> send(
            @Valid @RequestBody SpamSendRequest req,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.send(policy.authorizeSend(req, CurrentUser.from(jwt))));
    }
}
