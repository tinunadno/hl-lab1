package com.example.spamer.user.dto;

import com.example.spamer.user.domain.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Пароль только принимается: в ответах его нет, в базе хранится BCrypt-хэш. */
public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Email @Size(max = 128) String email,
        @NotNull UserRole role,
        @DecimalMin(value = "0.0") BigDecimal balance,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72) String password) {

    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", email=" + email
                + ", role=" + role + ", balance=" + balance + ", password=***]";
    }
}
