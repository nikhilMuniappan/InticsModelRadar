package com.intics.modelradar.repository;

import com.intics.modelradar.domain.AdoptionEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Append-only timeline of INTICS status changes. */
public interface AdoptionEventRepository extends JpaRepository<AdoptionEvent, Long> {

    /** One model's timeline, newest first. */
    List<AdoptionEvent> findByModelIdOrderByChangedAtDesc(String modelId);

    /** Company-wide activity feed, newest first. */
    @Query("select e from AdoptionEvent e order by e.changedAt desc")
    List<AdoptionEvent> recent(Pageable pageable);
}
