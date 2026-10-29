package com.tripledger.core.cleaning;

import com.tripledger.core.model.Ping;
import java.util.List;

// cleaned pings (sorted by time) + the report of what got removed
public record CleaningResult(List<Ping> pings, CleaningReport report) {

    public CleaningResult {
        pings = List.copyOf(pings);
    }
}
