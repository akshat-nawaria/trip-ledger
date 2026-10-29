package com.tripledger.app.service;

// -> 409. Two admins saved settings at the same moment; one wins, the other is told to reload and retry.
public class ConfigConflictException extends RuntimeException {

    public ConfigConflictException(String message) {
        super(message);
    }
}
