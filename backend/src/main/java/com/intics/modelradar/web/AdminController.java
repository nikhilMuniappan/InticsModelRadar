package com.intics.modelradar.web;

import com.intics.modelradar.domain.SyncTrigger;
import com.intics.modelradar.service.ModelSyncService.SyncSummary;
import com.intics.modelradar.service.SyncRunService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin/ops endpoints for running the tracker by hand. Goes through
 * {@link SyncRunService} so the run is recorded in the {@code sync_runs} audit trail.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SyncRunService syncRunService;

    public AdminController(SyncRunService syncRunService) {
        this.syncRunService = syncRunService;
    }

    /** Trigger a full Hugging Face sync now and return a summary of what happened. */
    @PostMapping("/sync")
    public SyncSummary sync() {
        return syncRunService.runAndRecord(SyncTrigger.MANUAL);
    }
}
