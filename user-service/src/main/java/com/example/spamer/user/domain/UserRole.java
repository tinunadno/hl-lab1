package com.example.spamer.user.domain;

import com.example.spamer.common.security.Roles;

/** Роли пользователей. Имена совпадают с {@link Roles}, они же попадают в JWT. */
public enum UserRole {
    SUPERVISOR,
    USER_FREE,
    USER_PRO,
    VICTIM,
    BUY_OPERATOR
}
