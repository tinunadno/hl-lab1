package com.example.spamer.user.dto;

import com.example.spamer.user.domain.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Частичное изменение. Супервайзер может менять любое поле, владелец профиля — только
 * {@code email} и {@code password}.
 */
public record PatchUserRequest(
        @Size(max = 64) String username,
        @Email @Size(max = 128) String email,
        UserRole role,
        @DecimalMin(value = "0.0") BigDecimal balance,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Size(min = 8, max = 72) String password) {

    /** Есть ли изменения, которые разрешены только супервайзеру. */
    public boolean touchesPrivilegedFields() {
        return username != null || role != null || balance != null;
    }

    @Override
    public String toString() {
        return "PatchUserRequest[username=" + username + ", email=" + email + ", role=" + role
                + ", balance=" + balance + ", password=" + (password == null ? null : "***") + "]";
    }
}
