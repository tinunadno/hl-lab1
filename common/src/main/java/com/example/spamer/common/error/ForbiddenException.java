package com.example.spamer.common.error;

/** Пользователь аутентифицирован, но это действие ему не разрешено. Отдаётся как 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
