package com.intics.modelradar.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

/**
 * Wires up ShedLock so the scheduled sync can never run twice concurrently — the
 * lock lives in the {@code shedlock} table (created by Flyway V2), so it holds even
 * if more than one instance of the app is ever running.
 *
 * <p>{@code defaultLockAtMostFor} is the safety net: if a holder dies mid-run without
 * releasing, the lock auto-expires after this so the job isn't wedged forever.
 * {@code usingDbTime()} makes ShedLock use the database clock, avoiding trouble when
 * app instances' clocks drift.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulingConfig {

    /**
     * The scheduler used for the daily {@code @Scheduled} cron and for the one-time
     * {@code SyncKickoff}. Pool size 2 so a kickoff and the daily job never block each
     * other (ShedLock still prevents them running the sync concurrently).
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("mr-sched-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
