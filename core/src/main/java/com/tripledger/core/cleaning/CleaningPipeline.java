package com.tripledger.core.cleaning;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.model.Ping;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the filters one after another and keeps count of what each one removed.
 *
 * The order matters and is part of the rule set:
 *   invalid coords -> duplicates -> time order -> low accuracy -> teleports
 * Teleport detection has to go last, it needs clean and sorted pings to work properly.
 */
public final class CleaningPipeline {

    private final List<PingFilter> filters;

    public CleaningPipeline(List<PingFilter> filters) {
        if (filters.isEmpty()) {
            throw new IllegalArgumentException("a cleaning pipeline needs at least one filter");
        }
        this.filters = List.copyOf(filters);
    }

    // the normal setup, with thresholds taken from the billing rules
    public static CleaningPipeline standard(BillingRules rules) {
        return new CleaningPipeline(List.of(
                new InvalidCoordinateFilter(),
                new DuplicateFilter(),
                new TimeOrderFilter(),
                new AccuracyFilter(rules.maxAccuracyMetres()),
                new TeleportFilter(rules.maxSpeedKmph(), rules.teleportReanchorRun())));
    }

    public CleaningResult clean(List<Ping> rawPings) {
        Map<String, Integer> removed = new LinkedHashMap<>();
        List<Ping> current = List.copyOf(rawPings);
        for (PingFilter filter : filters) {
            List<Ping> next = filter.apply(current);
            removed.put(filter.name(), current.size() - next.size());
            current = next;
        }
        CleaningReport report = new CleaningReport(rawPings.size(), removed, current.size());
        return new CleaningResult(current, report);
    }

    public List<String> filterNames() {
        return filters.stream().map(PingFilter::name).toList();
    }
}
