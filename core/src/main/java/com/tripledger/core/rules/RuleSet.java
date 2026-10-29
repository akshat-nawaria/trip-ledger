package com.tripledger.core.rules;

import com.tripledger.core.EngineInfo;
import java.util.List;
import java.util.Optional;

/**
 * The rules, in the order they're tried. First one that applies wins.
 *
 * The order is part of the version. If we ever reorder or change a rule, bump the version
 * so old bills can still be reproduced with the rules they were made with.
 */
public final class RuleSet {

    private final String version;
    private final List<SegmentRule> rules;

    public RuleSet(String version, List<SegmentRule> rules) {
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("a rule set needs at least one rule");
        }
        this.version = version;
        this.rules = List.copyOf(rules);
    }

    // the one from the case study
    public static RuleSet v1() {
        return new RuleSet(EngineInfo.RULE_SET_VERSION, List.of(
                new ImpossibleSpeedRule(),     // 0
                new ShortGapRule(),            // 1
                new ParkedRule(),              // 2
                new RoutingUnavailableRule(),  // 3
                new RouteTooLongRule(),        // 4
                new RouteTooFastRule(),        // 5
                new RoutedRule()));            // 6
    }

    public String version() {
        return version;
    }

    public SegmentDecision classify(SegmentContext segment) {
        for (SegmentRule rule : rules) {
            Optional<SegmentDecision> decision = rule.apply(segment);
            if (decision.isPresent()) {
                return decision.get();
            }
        }
        // can't happen with v1 (rule 6 or rule 3 always matches), but fail loudly if someone builds a broken set
        throw new IllegalStateException("no rule matched segment " + segment.from() + " -> " + segment.to());
    }

    public List<SegmentRule> rules() {
        return rules;
    }
}
