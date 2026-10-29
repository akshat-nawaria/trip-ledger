package com.tripledger.app.api.dto;

import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.Ping;
import com.tripledger.core.model.PlannedStop;
import com.tripledger.core.model.RiderType;
import com.tripledger.core.model.TripType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * One upload of trip evidence. A device can send a trip in several uploads (say every few minutes);
 * they're merged, and anything sent twice is ignored.
 *
 * What we validate here is only the SHAPE: required fields present, sizes sane. We deliberately do NOT
 * reject a ping because its coordinates look wrong - junk GPS is normal, and the cleaning filters
 * deal with it (and count it in the breakdown). Rejecting it here would just make devices retry forever.
 */
@Schema(description = "Pings, milestones and plan for one trip. Can be split over several uploads.")
public record EvidenceRequest(
        @NotNull TripType tripType,
        @NotNull OffsetDateTime windowStart,
        @NotNull OffsetDateTime windowEnd,
        @NotNull @Valid Point plannedOffice,
        @NotNull @Size(max = MAX_PINGS) @Valid List<@NotNull PingIn> pings,
        @Size(max = MAX_EVENTS) @Valid List<@NotNull MilestoneIn> milestones,
        @Size(max = MAX_EVENTS) @Valid List<@NotNull PlannedStopIn> plannedStops) {

    public static final int MAX_PINGS = 20_000;
    public static final int MAX_EVENTS = 1_000;

    public record Point(@NotNull Double lat, @NotNull Double lon) {

        GeoPoint toGeoPoint() {
            return GeoPoint.ofDegrees(lat, lon);
        }
    }

    public record PingIn(
            @NotNull OffsetDateTime ts,
            @NotNull Double lat,
            @NotNull Double lon,
            @NotNull @PositiveOrZero Integer accuracyM,
            Long seq) {

        Ping toPing() {
            return new Ping(micros(ts), GeoPoint.ofDegrees(lat, lon), accuracyM, seq == null ? 0 : seq);
        }
    }

    // employeeRef is empty for TRIP_START / TRIP_END. Point and time are optional on purpose:
    // driver apps really do send drops without a location, and the anchor rules know what to do then.
    public record MilestoneIn(String employeeRef, @NotNull MilestoneType type, Double lat, Double lon,
            OffsetDateTime ts) {

        Milestone toMilestone() {
            return new Milestone(employeeRef, type, optionalPoint(lat, lon), micros(ts));
        }
    }

    public record PlannedStopIn(@NotBlank String employeeRef, @NotNull RiderType riderType, Double lat,
            Double lon, OffsetDateTime plannedTs) {

        PlannedStop toPlannedStop() {
            return new PlannedStop(employeeRef, riderType, optionalPoint(lat, lon), micros(plannedTs));
        }
    }

    public Instant windowStartInstant() {
        return micros(windowStart);
    }

    public Instant windowEndInstant() {
        return micros(windowEnd);
    }

    public GeoPoint plannedOfficePoint() {
        return plannedOffice.toGeoPoint();
    }

    public List<Ping> toPings() {
        return pings.stream().map(PingIn::toPing).toList();
    }

    public List<Milestone> toMilestones() {
        return milestones == null ? List.of() : milestones.stream().map(MilestoneIn::toMilestone).toList();
    }

    public List<PlannedStop> toPlannedStops() {
        return plannedStops == null ? List.of() : plannedStops.stream().map(PlannedStopIn::toPlannedStop).toList();
    }

    // Postgres keeps time to the microsecond. Trim here, once, so a timestamp reads back exactly as it
    // went in - otherwise a re-sent ping or trip window could look "different" after a round trip.
    private static Instant micros(OffsetDateTime time) {
        return time == null ? null : time.toInstant().truncatedTo(ChronoUnit.MICROS);
    }

    private static GeoPoint optionalPoint(Double lat, Double lon) {
        return lat == null || lon == null ? null : GeoPoint.ofDegrees(lat, lon);
    }
}
