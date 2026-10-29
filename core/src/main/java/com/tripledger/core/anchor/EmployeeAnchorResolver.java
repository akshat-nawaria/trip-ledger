package com.tripledger.core.anchor;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.model.Anchor;
import com.tripledger.core.model.AnchorKind;
import com.tripledger.core.model.AnchorSource;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.PlannedStop;
import com.tripledger.core.model.RiderType;
import com.tripledger.core.model.TripEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns every pickup/drop into an anchor point.
 *
 * Order of trust, straight from the case study:
 *   1. what the driver app actually reported (if the point and time look sane)
 *   2. ad-hoc rider: the home address in their profile + the time they signed in
 *   3. planned rider: the planned location + planned time
 *
 * If none of these are usable we skip the employee rather than invent a point.
 *
 * Note: we only anchor employees that actually have a pickup/drop milestone. Someone on the
 * roster with no milestone at all gives us no proof they rode, so we don't add a detour for them.
 */
public final class EmployeeAnchorResolver implements AnchorResolver {

    @Override
    public List<Anchor> resolve(TripEvidence trip, BillingRules rules) {
        List<Anchor> anchors = new ArrayList<>();
        List<Milestone> rides = trip.milestones().stream()
                .filter(m -> m.type() == MilestoneType.PICKUP || m.type() == MilestoneType.DROP)
                .sorted(AnchorChecks.MILESTONE_ORDER)
                .toList();

        for (Milestone ride : rides) {
            AnchorKind kind = ride.type() == MilestoneType.PICKUP ? AnchorKind.EMPLOYEE_PICKUP : AnchorKind.EMPLOYEE_DROP;
            anchorFor(ride, kind, trip, rules).ifPresent(anchors::add);
        }
        return anchors;
    }

    private Optional<Anchor> anchorFor(Milestone ride, AnchorKind kind, TripEvidence trip, BillingRules rules) {
        if (AnchorChecks.usable(ride.point(), ride.time(), trip, rules)) {
            return Optional.of(new Anchor(kind, ride.employeeRef(), ride.point(), ride.time(), AnchorSource.ACTUAL));
        }

        // actual is junk (missing, (0,0), wrong clock...) - fall back to what we know from the plan
        Optional<PlannedStop> stop = AnchorChecks.plannedStopFor(ride.employeeRef(), trip.plannedStops());
        if (stop.isEmpty() || !AnchorChecks.usable(stop.get().point(), stop.get().time(), trip, rules)) {
            return Optional.empty();
        }
        PlannedStop plan = stop.get();
        AnchorSource source = plan.riderType() == RiderType.ADHOC ? AnchorSource.PROFILE_HOME : AnchorSource.PLANNED;
        return Optional.of(new Anchor(kind, ride.employeeRef(), plan.point(), plan.time(), source));
    }
}
