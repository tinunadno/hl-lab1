package com.example.spamer.user.dto;

import com.example.spamer.user.domain.UserRole;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;


public record PatchUserRequest(
        @Size(max = 64) String username,
        @Email @Size(max = 128) String email,
        UserRole role,
        @DecimalMin(value = "0.0") BigDecimal balance) {
}
