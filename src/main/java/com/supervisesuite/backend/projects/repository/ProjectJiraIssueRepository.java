package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectJiraIssueRepository extends JpaRepository<ProjectJiraIssue, UUID> {

    List<ProjectJiraIssue> findAllByProjectId(UUID projectId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatusCategoryKey(UUID projectId, String statusCategoryKey);

    long countByProjectIdAndDueDateBeforeAndStatusCategoryKeyNot(
            UUID projectId, LocalDate today, String statusCategoryKey);

    @Modifying
    void deleteAllByProjectIdAndIssueKeyNotIn(UUID projectId, List<String> issueKeys);

    @Modifying
    void deleteAllByProjectId(UUID projectId);

    @Query("SELECT MAX(i.syncedAt) FROM ProjectJiraIssue i WHERE i.projectId = :projectId")
    Optional<Instant> findMaxSyncedAtByProjectId(@Param("projectId") UUID projectId);

    /**
     * Returns all issues for the given project scoped to workload computation.
     * <p>
     * Fetching the full entity intentionally — the workload service performs an
     * O(n) in-memory reduction over all columns (assignee, status, story points,
     * dueDate, parentKey, parentIssueType) and benefits from a single round-trip
     * rather than a wide projection that would complicate the service layer.
     * </p>
     */
    @Query("""
            SELECT i FROM ProjectJiraIssue i
            WHERE i.projectId = :projectId
            ORDER BY i.issueKey ASC
            """)
    List<ProjectJiraIssue> findAllForWorkloadByProjectId(@Param("projectId") UUID projectId);

    /**
     * Returns {@code true} if at least one issue for this project has a non-null due date.
     * Used by the workload service to determine whether the {@code dueDateAvailable} flag
     * should be set, which controls whether the frontend shows an "estimate" badge on the
     * Overdue column header.
     */
    @Query("""
            SELECT COUNT(i) > 0 FROM ProjectJiraIssue i
            WHERE i.projectId = :projectId
              AND i.dueDate IS NOT NULL
            """)
    boolean existsByProjectIdAndDueDateIsNotNull(@Param("projectId") UUID projectId);
}
