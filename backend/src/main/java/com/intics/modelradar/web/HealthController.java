package com.intics.modelradar.web;

import com.intics.modelradar.domain.SyncRun;
import com.intics.modelradar.service.SyncRunService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Liveness/freshness check for the 7-day acceptance monitoring (Section 10 #7):
 * returns {@code 200 UP} only if the last <em>successful</em> sync finished within
 * the last 26 hours (a daily 08:00 job plus a couple of hours' grace), otherwise
 * {@code 503 DOWN}. Poll this once a day during the acceptance period.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /** Daily cadence + grace: a healthy system syncs at least this often. */
    static final Duration MAX_SYNC_AGE = Duration.ofHours(26);

    private final SyncRunService syncRunService;

    public HealthController(SyncRunService syncRunService) {
        this.syncRunService = syncRunService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Optional<SyncRun> last = syncRunService.lastSuccessfulRun();
        Instant now = Instant.now();

        boolean healthy = last.isPresent()
                && last.get().getFinishedAt() != null
                && Duration.between(last.get().getFinishedAt(), now).compareTo(MAX_SYNC_AGE) <= 0;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", healthy ? "UP" : "DOWN");
        body.put("maxSyncAgeHours", MAX_SYNC_AGE.toHours());
        body.put("lastSuccessfulSyncAt", last.map(SyncRun::getFinishedAt).orElse(null));
        last.ifPresent(run -> {
            body.put("lastSyncModelsChecked", run.getModelsChecked());
            body.put("lastSyncNew", run.getNewCount());
            body.put("lastSyncUpdated", run.getUpdatedCount());
        });

        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }
}
