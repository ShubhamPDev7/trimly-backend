package com.trimly.backend.exception;

public class ShopAccessDeniedException extends RuntimeException{

    public ShopAccessDeniedException(String message) {
        super(message);
    }

}
