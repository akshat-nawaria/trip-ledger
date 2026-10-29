package com.tripledger.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// starts the TripLedger service
@SpringBootApplication
public class TripLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripLedgerApplication.class, args);
    }
}
