package com.example.spamer.dispatch.dto;

import com.example.spamer.dispatch.domain.SpamStatus;
import jakarta.validation.constraints.Size;

public record PatchSpamLogRequest(
        @Size(max = 256) String victimContact,
        String messageBody,
        SpamStatus status) {
}
