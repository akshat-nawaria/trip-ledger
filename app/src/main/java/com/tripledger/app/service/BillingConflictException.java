package com.tripledger.app.service;

// -> 409. The trip is already on another invoice, or there's nothing computed yet to bill.
public class BillingConflictException extends RuntimeException {

    public BillingConflictException(String message) {
        super(message);
    }
}
