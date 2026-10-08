package com.pedidos360.rabbitadmin.exception;

public class ProtectedResourceException extends RuntimeException {
    public ProtectedResourceException(String message) {
        super(message);
    }
}
