package com.intics.modelradar.repository;

import com.intics.modelradar.domain.AdoptionStatus;
import com.intics.modelradar.domain.ModelAdoption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Persistence for the INTICS working state of a model (one row per marked model). */
public interface ModelAdoptionRepository extends JpaRepository<ModelAdoption, String> {

    /** How many models sit at each stage (stages with no rows are simply absent). */
    @Query("select a.status as status, count(a) as count from ModelAdoption a group by a.status")
    List<StatusCount> countGroupedByStatus();

    /** Distinct non-blank owners, alphabetical — feeds the "Owner" suggestion list. */
    @Query("select distinct a.owner from ModelAdoption a"
            + " where a.owner is not null and a.owner <> '' order by a.owner")
    List<String> distinctOwners();

    /** Distinct non-blank teams, alphabetical — feeds the "Team" suggestion list. */
    @Query("select distinct a.team from ModelAdoption a"
            + " where a.team is not null and a.team <> '' order by a.team")
    List<String> distinctTeams();

    /** Most recently touched adoptions — the "active work" feed on the dashboard. */
    @Query("select a from ModelAdoption a order by a.updatedAt desc")
    List<ModelAdoption> recentlyUpdated(org.springframework.data.domain.Pageable pageable);

    /** All models currently at one of the given stages. */
    List<ModelAdoption> findByStatusIn(List<AdoptionStatus> statuses);

    /** Count of models at one specific stage. */
    long countByStatus(@Param("status") AdoptionStatus status);

    /** Projection for {@link #countGroupedByStatus()}. */
    interface StatusCount {
        AdoptionStatus getStatus();

        long getCount();
    }
}
