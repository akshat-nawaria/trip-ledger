package com.tripledger.app.service;

// -> 400. Well-formed JSON that still makes no sense (window ends before it starts, bad trip id...)
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
