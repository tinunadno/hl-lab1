package com.example.spamer.common.error;


public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
