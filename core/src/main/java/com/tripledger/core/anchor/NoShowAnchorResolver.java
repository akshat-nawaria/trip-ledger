package com.tripledger.core.anchor;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.model.Anchor;
import com.tripledger.core.model.AnchorKind;
import com.tripledger.core.model.AnchorSource;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.PlannedStop;
import com.tripledger.core.model.TripEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * When an employee doesn't show up, the cab still drove to their house.
 * Whether the vendor gets paid for that drive is a business decision, so it sits behind
 * the noShowAnchorEnabled flag (off by default).
 *
 * With the flag on: use the no-show location the driver reported, else the planned location.
 */
public final class NoShowAnchorResolver implements AnchorResolver {

    @Override
    public List<Anchor> resolve(TripEvidence trip, BillingRules rules) {
        if (!rules.noShowAnchorEnabled()) {
            return List.of();
        }
        List<Anchor> anchors = new ArrayList<>();
        trip.milestones().stream()
                .filter(m -> m.type() == MilestoneType.NO_SHOW)
                .sorted(AnchorChecks.MILESTONE_ORDER)
                .forEach(m -> anchorFor(m, trip, rules).ifPresent(anchors::add));
        return anchors;
    }

    private Optional<Anchor> anchorFor(Milestone noShow, TripEvidence trip, BillingRules rules) {
        if (AnchorChecks.usable(noShow.point(), noShow.time(), trip, rules)) {
            return Optional.of(new Anchor(AnchorKind.NO_SHOW, noShow.employeeRef(), noShow.point(), noShow.time(),
                    AnchorSource.ACTUAL));
        }
        Optional<PlannedStop> stop = AnchorChecks.plannedStopFor(noShow.employeeRef(), trip.plannedStops());
        if (stop.isPresent() && AnchorChecks.usable(stop.get().point(), stop.get().time(), trip, rules)) {
            return Optional.of(new Anchor(AnchorKind.NO_SHOW, noShow.employeeRef(), stop.get().point(),
                    stop.get().time(), AnchorSource.PLANNED));
        }
        return Optional.empty();
    }
}
