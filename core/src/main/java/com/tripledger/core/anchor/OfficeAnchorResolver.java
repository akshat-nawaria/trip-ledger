package com.tripledger.core.anchor;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.geo.Haversine;
import com.tripledger.core.model.Anchor;
import com.tripledger.core.model.AnchorKind;
import com.tripledger.core.model.AnchorSource;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.TripEvidence;
import com.tripledger.core.model.TripType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Pins the office end of the trip.
 *
 * Login trip (home -> office): the office is where the trip ENDS.
 * Logout trip (office -> home): the office is where it STARTS.
 *
 * We only trust the actual end/start point if it's within 400 m (officeRadiusMetres) of the
 * planned office. Further than that and it's usually GPS junk in the basement or the driver
 * pressing "end trip" a few streets away - then we use the office location with the reported time.
 */
public final class OfficeAnchorResolver implements AnchorResolver {

    @Override
    public List<Anchor> resolve(TripEvidence trip, BillingRules rules) {
        boolean login = trip.tripType() == TripType.LOGIN;
        MilestoneType officeEvent = login ? MilestoneType.TRIP_END : MilestoneType.TRIP_START;
        Instant fallbackTime = login ? trip.windowEnd() : trip.windowStart();

        Optional<Milestone> event = pick(trip.milestones(), officeEvent, login);

        if (event.isPresent()) {
            Milestone m = event.get();
            if (AnchorChecks.usable(m.point(), m.time(), trip, rules) && closeToOffice(m, trip, rules)) {
                return List.of(new Anchor(AnchorKind.OFFICE, null, m.point(), m.time(), AnchorSource.ACTUAL));
            }
            if (m.time() != null && AnchorChecks.insideWindow(m.time(), trip, rules)) {
                fallbackTime = m.time();
            }
        }
        return List.of(new Anchor(AnchorKind.OFFICE, null, trip.plannedOffice(), fallbackTime,
                AnchorSource.PLANNED_OFFICE));
    }

    // if a buggy app sent the event twice, take the latest end / earliest start.
    // ones with a timestamp win over ones without.
    private Optional<Milestone> pick(List<Milestone> milestones, MilestoneType type, boolean latest) {
        List<Milestone> found = milestones.stream()
                .filter(m -> m.type() == type)
                .sorted(AnchorChecks.MILESTONE_ORDER)
                .toList();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        List<Milestone> timed = found.stream().filter(m -> m.time() != null).toList();
        List<Milestone> pool = timed.isEmpty() ? found : timed;
        return Optional.of(latest ? pool.get(pool.size() - 1) : pool.get(0));
    }

    private boolean closeToOffice(Milestone m, TripEvidence trip, BillingRules rules) {
        return Haversine.metresBetween(m.point(), trip.plannedOffice()) <= rules.officeRadiusMetres();
    }
}
