package com.example.spamer.user.service;

/** Неверное имя или пароль. Причину намеренно не уточняем: отдаётся как 401. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
