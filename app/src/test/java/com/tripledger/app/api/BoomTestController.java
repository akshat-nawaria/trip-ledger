package com.tripledger.app.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// test-only endpoint (lives in src/test, never shipped) that fails the way a real bug would -
// with an ugly internal message that must NOT reach the client
@RestController
public class BoomTestController {

    @GetMapping("/v1/test/boom")
    public String boom() {
        throw new IllegalStateException("simulated bug: SELECT card_number FROM secrets WHERE id = 42");
    }
}
