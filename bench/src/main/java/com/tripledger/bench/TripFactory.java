package com.tripledger.bench;

import com.tripledger.core.geo.Haversine;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.Ping;
import com.tripledger.core.model.PlannedStop;
import com.tripledger.core.model.RiderType;
import com.tripledger.core.model.TripEvidence;
import com.tripledger.core.model.TripType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Makes believable fake trips for the benchmark. Same seed, same trips, on every machine.
 *
 * The city: one office, a few hundred regular cab routes (an evening logout shift drops the same
 * people at the same homes every day). Each trip drives one of those routes:
 *
 *   office -> 30-40 km of roads with turns -> 2-4 drops, the last one at the end of the route
 *   ping every 4 s (about 1,500 per trip), speed 16-28 km/h, waits at signals and at every drop
 *
 * ...and the GPS behaves like real phone GPS does:
 *
 *   - jitter of a few metres on every fix (less while standing still, phones smooth that out)
 *   - 1% bad fixes (accuracy 60-150 m)          -> the accuracy filter drops them
 *   - 0.2% teleports, 5-30 km off               -> the teleport filter drops them
 *   - 0.5% duplicates and some out-of-order pings -> dedupe + sort
 *   - 1-3 dead zones per route (underpasses, tunnels), 0.5-2 km with no GPS at all
 *     -> real gaps that the engine has to route. Every third route has a drop INSIDE a dead
 *        zone, which is exactly the underpass case from the case study
 *   - 4% of drops arrive without a location     -> the planned stop is used instead
 *
 * Trips are made one at a time and thrown away after use, so 10,000 trips never sit in memory together.
 */
public final class TripFactory {

    public static final GeoPoint OFFICE = GeoPoint.ofDegrees(12.9698, 77.7500);

    static final int PING_EVERY_SECONDS = 4;

    private static final double METRES_PER_DEGREE = Haversine.EARTH_RADIUS_METRES * Math.PI / 180.0;
    private static final double METRES_PER_DEGREE_LON = METRES_PER_DEGREE * Math.cos(Math.toRadians(OFFICE.latitude()));
    // the logout shift leaves the office at 18:00 IST
    private static final Instant SHIFT_START = Instant.parse("2026-10-29T12:30:00Z");

    private final long seed;
    private final List<Route> routes;

    public TripFactory(long seed, int routeCount) {
        if (routeCount <= 0) {
            throw new IllegalArgumentException("need at least one route");
        }
        this.seed = seed;
        SplittableRandom random = new SplittableRandom(seed);
        List<Route> made = new ArrayList<>(routeCount);
        for (int i = 0; i < routeCount; i++) {
            made.add(Route.random(i, random.split()));
        }
        this.routes = List.copyOf(made);
    }

    public int routeCount() {
        return routes.size();
    }

    // the same index always gives exactly the same trip
    public TripEvidence trip(int index) {
        SplittableRandom random = new SplittableRandom(seed * 1_000_003L + index);
        Route route = routes.get(random.nextInt(routes.size()));
        Instant start = SHIFT_START.plusSeconds(random.nextInt(0, 3_600));
        return new Drive(route, random, start).toEvidence("B-" + index);
    }

    // ---- the city ----------------------------------------------------------------------------------

    // a point along a route, in metres east / north of the office
    record Xy(double east, double north) {
    }

    record Stop(double at, String employeeRef, boolean insideDeadZone) {
    }

    record DeadZone(double from, double to) {

        boolean covers(double s) {
            return s > from && s < to;
        }
    }

    record Route(int id, List<Xy> corners, double[] cumulative, List<Stop> drops, List<DeadZone> deadZones,
            double[] signals) {

        static Route random(int id, SplittableRandom r) {
            double target = r.nextDouble(30_000, 40_000);
            List<Xy> corners = new ArrayList<>();
            corners.add(new Xy(0, 0));
            double heading = r.nextInt(8) * Math.PI / 4;
            double length = 0;
            while (length < target) {
                double leg = Math.min(r.nextDouble(600, 3_200), target - length + 1);
                Xy last = corners.get(corners.size() - 1);
                corners.add(new Xy(last.east() + leg * Math.sin(heading), last.north() + leg * Math.cos(heading)));
                length += leg;
                // city driving: mostly straight on or a right-angle turn, sometimes a bend
                int turn = r.nextInt(10);
                heading += turn < 4 ? 0 : turn < 7 ? Math.PI / 2 : turn < 9 ? -Math.PI / 2 : (r.nextBoolean() ? 1 : -1) * Math.PI / 4;
            }
            double[] cumulative = new double[corners.size()];
            for (int i = 1; i < corners.size(); i++) {
                Xy a = corners.get(i - 1);
                Xy b = corners.get(i);
                cumulative[i] = cumulative[i - 1] + Math.hypot(b.east() - a.east(), b.north() - a.north());
            }
            double total = cumulative[cumulative.length - 1];

            List<DeadZone> zones = new ArrayList<>();
            int zoneCount = r.nextInt(1, 4);
            for (int z = 0; z < zoneCount; z++) {
                // spread them out so two zones never overlap
                double slot = total / zoneCount;
                double from = slot * z + r.nextDouble(0.15, 0.6) * slot;
                zones.add(new DeadZone(from, from + r.nextDouble(500, 2_000)));
            }

            int dropCount = r.nextInt(2, 5);
            List<Stop> drops = new ArrayList<>();
            boolean underpassDrop = id % 3 == 0;
            for (int d = 0; d < dropCount - 1; d++) {
                double at = total * (0.35 + 0.6 * (d + r.nextDouble(0.2, 0.8)) / dropCount);
                drops.add(new Stop(at, "e-" + id + "-" + d, false));
            }
            if (underpassDrop) {
                DeadZone zone = zones.get(0);
                drops.add(new Stop((zone.from() + zone.to()) / 2, "e-" + id + "-u", true));
            }
            drops.add(new Stop(total, "e-" + id + "-last", false));
            drops.sort((a, b) -> Double.compare(a.at(), b.at()));

            // a traffic signal every 1.2-2.5 km
            List<Double> signalList = new ArrayList<>();
            for (double s = r.nextDouble(800, 2_000); s < total - 500; s += r.nextDouble(1_200, 2_500)) {
                signalList.add(s);
            }
            double[] signals = signalList.stream().mapToDouble(Double::doubleValue).toArray();
            return new Route(id, List.copyOf(corners), cumulative, List.copyOf(drops), List.copyOf(zones), signals);
        }

        double length() {
            return cumulative[cumulative.length - 1];
        }

        Xy pointAt(double s) {
            if (s <= 0) {
                return corners.get(0);
            }
            if (s >= length()) {
                return corners.get(corners.size() - 1);
            }
            int lo = 0;
            int hi = cumulative.length - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (cumulative[mid] <= s) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            double t = (s - cumulative[lo]) / (cumulative[hi] - cumulative[lo]);
            Xy a = corners.get(lo);
            Xy b = corners.get(hi);
            return new Xy(a.east() + t * (b.east() - a.east()), a.north() + t * (b.north() - a.north()));
        }

        boolean inDeadZone(double s) {
            for (DeadZone zone : deadZones) {
                if (zone.covers(s)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ---- one drive along a route --------------------------------------------------------------------

    private static final class Drive {

        private final Route route;
        private final SplittableRandom r;
        private final Instant start;
        private final List<Ping> pings = new ArrayList<>(1_800);
        private final List<Milestone> milestones = new ArrayList<>();
        private final List<PlannedStop> planned = new ArrayList<>();
        private final double cruise;
        private long seq;

        Drive(Route route, SplittableRandom r, Instant start) {
            this.route = route;
            this.r = r;
            this.start = start;
            this.cruise = r.nextDouble(16, 28) / 3.6; // m/s
        }

        TripEvidence toEvidence(String tripId) {
            milestones.add(new Milestone(null, MilestoneType.TRIP_START, jitter(new Xy(0, 0), 5), start));
            simulate();
            shuffleALittle();
            return new TripEvidence(tripId, TripType.LOGOUT, start.minusSeconds(300), start.plusSeconds(4 * 3_600),
                    OFFICE, pings, milestones, planned);
        }

        // move along the route 4 s at a time; stop at signals and drops
        private void simulate() {
            List<double[]> stops = new ArrayList<>(); // {position, seconds, dropIndex or -1}
            for (double s : route.signals()) {
                stops.add(new double[] {s, r.nextDouble(0, 1) < 0.5 ? r.nextDouble(15, 90) : 0, -1});
            }
            for (int d = 0; d < route.drops().size(); d++) {
                stops.add(new double[] {route.drops().get(d).at(), r.nextDouble(45, 150), d});
            }
            stops.sort((a, b) -> Double.compare(a[0], b[0]));

            double s = 0;
            double clock = 0;
            double wait = 0;
            int next = 0;
            double legSpeed = cruise;
            while (true) {
                if (!route.inDeadZone(s)) {
                    addPing(clock, route.pointAt(s), wait > 0);
                }
                if (s >= route.length() && next >= stops.size() && wait <= 0) {
                    break;
                }
                double dt = PING_EVERY_SECONDS;
                while (dt > 1e-9) {
                    if (wait > 0) {
                        double w = Math.min(wait, dt);
                        wait -= w;
                        dt -= w;
                        continue;
                    }
                    if (next < stops.size() && s + legSpeed * dt >= stops.get(next)[0]) {
                        double[] stop = stops.get(next++);
                        double travel = Math.max(0, stop[0] - s) / legSpeed;
                        dt -= travel;
                        s = stop[0];
                        wait = stop[1];
                        if (stop[2] >= 0) {
                            recordDrop((int) stop[2], clock + (PING_EVERY_SECONDS - dt));
                        }
                        legSpeed = cruise * r.nextDouble(0.8, 1.2); // traffic changes after every stop
                        continue;
                    }
                    s = Math.min(route.length(), s + legSpeed * dt);
                    dt = 0;
                    if (s >= route.length() && next >= stops.size()) {
                        break;
                    }
                }
                clock += PING_EVERY_SECONDS;
            }
        }

        private void recordDrop(int dropIndex, double atSecond) {
            Stop drop = route.drops().get(dropIndex);
            Instant when = start.plusMillis(Math.round(atSecond * 1_000));
            Xy home = route.pointAt(drop.at());
            GeoPoint reported = r.nextDouble() < 0.04 ? null : jitter(home, 8); // 4% come without a location
            milestones.add(new Milestone(drop.employeeRef(), MilestoneType.DROP, reported, when));
            planned.add(new PlannedStop(drop.employeeRef(), RiderType.PLANNED, toGeo(home),
                    when.plusSeconds(r.nextInt(-600, 600))));
        }

        private void addPing(double atSecond, Xy where, boolean standingStill) {
            Instant ts = start.plusMillis(Math.round(atSecond * 1_000));
            double roll = r.nextDouble();
            Ping ping;
            if (roll < 0.002) {
                // teleport: a fix kilometres away that looks perfectly confident
                double km = r.nextDouble(5_000, 30_000);
                double angle = r.nextDouble(0, 2 * Math.PI);
                ping = new Ping(ts, toGeo(new Xy(where.east() + km * Math.sin(angle), where.north() + km * Math.cos(angle))),
                        r.nextInt(5, 15), seq++);
            } else if (roll < 0.012) {
                ping = new Ping(ts, jitter(where, r.nextDouble(60, 200)), r.nextInt(60, 150), seq++);
            } else {
                ping = new Ping(ts, jitter(where, standingStill ? 1.5 : 3), r.nextInt(3, 13), seq++);
            }
            pings.add(ping);
            if (r.nextDouble() < 0.005) {
                pings.add(ping); // the phone sent the same fix twice
            }
        }

        // phones upload in batches, and batches don't always arrive in order
        private void shuffleALittle() {
            for (int i = 1; i < pings.size(); i++) {
                if (r.nextDouble() < 0.02) {
                    Ping a = pings.get(i - 1);
                    pings.set(i - 1, pings.get(i));
                    pings.set(i, a);
                }
            }
        }

        private GeoPoint jitter(Xy where, double metres) {
            return toGeo(new Xy(where.east() + gaussian() * metres, where.north() + gaussian() * metres));
        }

        // Box-Muller, so we only need the seeded SplittableRandom
        private double gaussian() {
            double u = 1.0 - r.nextDouble();
            double v = r.nextDouble();
            return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
        }
    }

    static GeoPoint toGeo(Xy xy) {
        return GeoPoint.ofDegrees(OFFICE.latitude() + xy.north() / METRES_PER_DEGREE,
                OFFICE.longitude() + xy.east() / METRES_PER_DEGREE_LON);
    }
}
