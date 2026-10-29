package com.tripledger.core.cleaning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small receipt of what cleaning did: pings in, how many each filter threw out, pings left.
 * Useful when an operator asks why the km look lower/higher than they expected.
 */
public record CleaningReport(int received, Map<String, Integer> removedByFilter, int kept) {

    public CleaningReport {
        removedByFilter = Collections.unmodifiableMap(new LinkedHashMap<>(removedByFilter));
    }

    public int removedBy(String filterName) {
        return removedByFilter.getOrDefault(filterName, 0);
    }

    public int totalRemoved() {
        return received - kept;
    }
}
