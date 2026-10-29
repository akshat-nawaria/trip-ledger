package com.tripledger.core.engine;

import com.tripledger.core.anchor.AnchorResolver;
import com.tripledger.core.anchor.EmployeeAnchorResolver;
import com.tripledger.core.anchor.NoShowAnchorResolver;
import com.tripledger.core.anchor.OfficeAnchorResolver;
import com.tripledger.core.cleaning.CleaningPipeline;
import com.tripledger.core.cleaning.CleaningResult;
import com.tripledger.core.config.BillingRules;
import com.tripledger.core.engine.RouteAnswer.Found;
import com.tripledger.core.geo.Distance;
import com.tripledger.core.model.Anchor;
import com.tripledger.core.model.RouteEvidence;
import com.tripledger.core.model.Segment;
import com.tripledger.core.model.SegmentLabel;
import com.tripledger.core.model.TrackPoint;
import com.tripledger.core.model.TripDistance;
import com.tripledger.core.model.TripEvidence;
import com.tripledger.core.model.TripStatus;
import com.tripledger.core.rules.RuleSet;
import com.tripledger.core.rules.SegmentContext;
import com.tripledger.core.rules.SegmentDecision;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The whole thing in one place:
 *   clean the pings -> add anchor points -> walk the track pair by pair and let the
 *   rules measure each piece -> add up the millimetres -> round once.
 *
 * No database, no HTTP, no clock in here. Give it the same evidence, rules and route
 * answers and you get exactly the same TripDistance back, every time.
 */
public final class DistanceEngine {

    private static final Comparator<Anchor> ANCHOR_ORDER = Comparator
            .comparing(Anchor::time)
            .thenComparing(Anchor::kind)
            .thenComparing(Anchor::employeeRef, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final BillingRules rules;
    private final RuleSet ruleSet;
    private final List<AnchorResolver> anchorResolvers;

    public DistanceEngine(BillingRules rules) {
        this(rules, RuleSet.v1());
    }

    public DistanceEngine(BillingRules rules, RuleSet ruleSet) {
        this.rules = rules;
        this.ruleSet = ruleSet;
        this.anchorResolvers = List.of(
                new EmployeeAnchorResolver(),
                new NoShowAnchorResolver(),
                new OfficeAnchorResolver());
    }

    public TripDistance compute(TripEvidence trip, RouteLookup routes) {
        CleaningResult cleaned = CleaningPipeline.forTrip(rules, trip.windowStart(), trip.windowEnd())
                .clean(trip.pings());
        List<Anchor> anchors = resolveAnchors(trip);
        List<TrackPoint> track = buildTrack(cleaned, anchors);

        List<Segment> segments = new ArrayList<>();
        List<RouteEvidence> routesUsed = new ArrayList<>();
        int routeRequests = 0;

        for (int i = 1; i < track.size(); i++) {
            SegmentContext context = new SegmentContext(track.get(i - 1), track.get(i), rules, routes);
            SegmentDecision decision = ruleSet.classify(context);
            segments.add(new Segment(i, context.from(), context.to(), decision.distance(), decision.label()));

            if (context.routeWasRequested()) {
                routeRequests++;
                // remember the road distances we actually paid on, so a recompute can replay them
                if (decision.label() == SegmentLabel.ROUTED && context.route() instanceof Found found) {
                    routesUsed.add(new RouteEvidence(context.from().point(), context.to().point(),
                            found.drivingDistance()));
                }
            }
        }

        Distance total = Distance.sum(segments.stream().map(Segment::distance).toList());
        TripStatus status = segments.stream().anyMatch(s -> s.label() == SegmentLabel.DEGRADED_AERIAL)
                ? TripStatus.DEGRADED
                : TripStatus.COMPUTED;

        return new TripDistance(trip.tripId(), ruleSet.version(), total, total.toBillableKm(), status,
                segments, anchors, cleaned.report(), routeRequests, routesUsed);
    }

    private List<Anchor> resolveAnchors(TripEvidence trip) {
        List<Anchor> anchors = new ArrayList<>();
        for (AnchorResolver resolver : anchorResolvers) {
            anchors.addAll(resolver.resolve(trip, rules));
        }
        anchors.sort(ANCHOR_ORDER);
        return anchors;
    }

    // pings and anchors go into one list, in time order
    private static List<TrackPoint> buildTrack(CleaningResult cleaned, List<Anchor> anchors) {
        List<TrackPoint> track = new ArrayList<>(cleaned.pings().size() + anchors.size());
        cleaned.pings().forEach(p -> track.add(TrackPoint.of(p)));
        anchors.forEach(a -> track.add(TrackPoint.of(a)));
        track.sort(TrackPoint.IN_ORDER);
        return track;
    }
}
