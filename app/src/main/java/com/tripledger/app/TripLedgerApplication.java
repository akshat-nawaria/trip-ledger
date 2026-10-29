package com.tripledger.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the billable-distance service. */
@SpringBootApplication
public class TripLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripLedgerApplication.class, args);
    }
}
