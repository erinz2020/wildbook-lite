package com.wildme.wildbook_lite.stats;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.stats.dto.ProjectStatsResponse;
import com.wildme.wildbook_lite.stats.dto.SpeciesCount;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Aggregations for project dashboards. Uses native SQL for the GROUP BY
 * queries because:
 *  - JPQL has limits around DTO projection from native aggregates.
 *  - We want to demonstrate "native query with named parameters" which is
 *    a common interview topic ("when do you drop down to native SQL?").
 *
 * All counts are computed in a single transaction so the snapshot is
 * internally consistent (no torn read across statements).
 */
@Service
public class StatsService {

    private final ProjectGuard projectGuard;

    @PersistenceContext
    private EntityManager em;

    public StatsService(ProjectGuard projectGuard) {
        this.projectGuard = projectGuard;
    }

    @Transactional(readOnly = true)
    public ProjectStatsResponse projectStats(Long projectId) {
        if (!projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to project: " + projectId);
        }

        long totalEncounters = countLong(
            "SELECT count(*) FROM encounter WHERE project_id = :p", projectId);
        long totalMembers = countLong(
            "SELECT count(*) FROM project_members WHERE project_id = :p", projectId);
        long totalMedia = countLong(
            "SELECT count(*) FROM media_asset m " +
            " JOIN encounter e ON e.id = m.encounter_id " +
            "WHERE e.project_id = :p", projectId);
        long totalComments = countLong(
            "SELECT count(*) FROM comments c " +
            " JOIN encounter e ON e.id = c.encounter_id " +
            "WHERE e.project_id = :p", projectId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT species, count(*) AS c " +
            "  FROM encounter " +
            " WHERE project_id = :p AND species IS NOT NULL " +
            " GROUP BY species " +
            " ORDER BY c DESC " +
            " LIMIT 5")
            .setParameter("p", projectId)
            .getResultList();

        List<SpeciesCount> topSpecies = rows.stream()
            .map(r -> new SpeciesCount(
                (String) r[0],
                ((Number) r[1]).longValue()))
            .toList();

        return new ProjectStatsResponse(
            projectId, totalEncounters, totalMembers, totalMedia, totalComments, topSpecies
        );
    }

    private long countLong(String sql, Long projectId) {
        Object v = em.createNativeQuery(sql)
            .setParameter("p", projectId)
            .getSingleResult();
        return ((Number) v).longValue();
    }
}
