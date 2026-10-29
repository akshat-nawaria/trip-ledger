package com.tripledger.app.api;

import static com.tripledger.app.api.TestEvidence.newKey;
import static com.tripledger.app.api.TestEvidence.newTripId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripledger.app.persistence.JobRepository;
import com.tripledger.app.service.DistanceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

// the compute queue: crash recovery, failure handling, and what happens when many workers / threads collide
@SpringBootTest
@AutoConfigureMockMvc
class JobsIntegrationTest extends ApiTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private DistanceService distance;

    @Test
    void anUploadQueuesAJobAndItIsFinishedRightAway() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());

        List<Map<String, Object>> rows = jobsOf(tripId);
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).get("status")).isEqualTo("DONE");
        assertThat(rows.get(0).get("reason")).isEqualTo("UPLOAD");
    }

    @Test
    void manyRequestsForTheSameTripStillMeanOneWaitingJob() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());

        for (int i = 0; i < 10; i++) {
            jobs.enqueue(tenant, tripId, JobRepository.Reason.MANUAL);
        }

        assertThat(count("SELECT count(*) FROM compute_job WHERE tenant_id = ? AND trip_id = ? AND status = 'PENDING'",
                tenant, tripId)).isEqualTo(1L);
        drainJobs();
    }

    @Test
    void aJobWhoseWorkerCrashedIsPickedUpAgainOnceItsLeaseRunsOut() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());
        // what a worker that died half way leaves behind: RUNNING, with a lease that's already over
        jdbc.update("""
                INSERT INTO compute_job (tenant_id, trip_id, reason, status, attempts, lease_until)
                VALUES (?, ?, 'MANUAL', 'RUNNING', 1, now() - interval '1 minute')
                """, tenant, tripId);

        drainJobs();

        Map<String, Object> rescued = jobsOf(tripId).get(1);
        assertThat(rescued.get("status")).isEqualTo("DONE");
        assertThat(((Number) rescued.get("attempts")).intValue()).isEqualTo(2);
    }

    @Test
    void aJobThatIsStillLeasedIsLeftAlone() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());
        // a healthy worker is busy with this trip right now
        jdbc.update("""
                INSERT INTO compute_job (tenant_id, trip_id, reason, status, attempts, lease_until)
                VALUES (?, ?, 'MANUAL', 'RUNNING', 1, now() + interval '5 minutes')
                """, tenant, tripId);

        drainJobs();

        assertThat(jobsOf(tripId).get(1).get("status")).isEqualTo("RUNNING");
        jdbc.update("UPDATE compute_job SET status = 'DONE' WHERE tenant_id = ? AND trip_id = ?", tenant, tripId);
    }

    @Test
    void aJobForATripThatDoesNotExistFailsWithoutRetrying() {
        String ghost = newTripId();
        jobs.enqueue(tenant, ghost, JobRepository.Reason.MANUAL);

        drainJobs();

        Map<String, Object> job = jobsOf(ghost).get(0);
        assertThat(job.get("status")).isEqualTo("FAILED");
        assertThat(job.get("last_error")).isEqualTo("trip not found");
        assertThat(((Number) job.get("attempts")).intValue()).isEqualTo(1);
    }

    @Test
    void whenEightWorkersGrabForTheSameJobExactlyOneGetsIt() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());
        jobs.enqueue(tenant, tripId, JobRepository.Reason.MANUAL);

        List<Optional<JobRepository.ClaimedJob>> results = all(8,
                () -> jobs.claimForTrip(tenant, tripId, java.time.Duration.ofMinutes(1)));

        assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1L);
        results.stream().flatMap(Optional::stream).forEach(job -> jobs.complete(job.id()));
    }

    @Test
    void eightThreadsComputingTheSameTripAtOnceSaveExactlyOneRun() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());
        // new settings -> new fingerprint -> every thread really has to compute, and they all race to save
        mvc.perform(put("/v1/tenant/config").header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noShowAnchorEnabled\":false,\"maxAccuracyMetres\":49,\"maxSpeedKmph\":120,"
                                + "\"officeRadiusMetres\":400,\"maxRouteKm\":100}"))
                .andExpect(status().isOk());

        List<DistanceService.Outcome> outcomes = all(8, () -> distance.compute(tenant, tripId));

        Set<UUID> runIds = new java.util.HashSet<>();
        outcomes.forEach(o -> runIds.add(o.runId()));
        assertThat(runIds.size()).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM computation_run
                WHERE tenant_id = ? AND trip_id = ? AND status = 'COMPUTED' AND config_version = 1
                """, tenant, tripId)).isEqualTo(1L);
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private List<Map<String, Object>> jobsOf(String tripId) {
        return jdbc.queryForList("SELECT * FROM compute_job WHERE tenant_id = ? AND trip_id = ? ORDER BY id",
                tenant, tripId);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    // runs the same task on n threads that all start at the same instant
    private static <T> List<T> all(int n, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
