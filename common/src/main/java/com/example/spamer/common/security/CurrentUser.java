package com.example.spamer.common.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Кто выполняет запрос, по данным проверенного JWT.
 *
 * @param id       id пользователя; {@code null}, если запрос пришёл от другого сервиса
 * @param username имя пользователя или сервиса
 * @param role     роль из {@link Roles}
 */
public record CurrentUser(UUID id, String username, String role) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                parseId(jwt.getSubject()),
                jwt.getClaimAsString(JwtSupport.USERNAME_CLAIM),
                jwt.getClaimAsString(JwtSupport.ROLE_CLAIM));
    }

    public boolean is(String expectedRole) {
        return expectedRole.equals(role);
    }

    public boolean isSupervisor() {
        return is(Roles.SUPERVISOR);
    }

    public boolean isService() {
        return is(Roles.SERVICE);
    }

    /** Супервайзер и внутренние вызовы видят чужие данные, остальные только свои. */
    public boolean canSeeEveryone() {
        return isSupervisor() || isService();
    }

    public boolean owns(UUID ownerId) {
        return id != null && id.equals(ownerId);
    }

    private static UUID parseId(String subject) {
        try {
            return subject == null ? null : UUID.fromString(subject);
        } catch (IllegalArgumentException notAUser) {
            return null;
        }
    }
}
