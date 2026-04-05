package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectJiraIssueRepository extends JpaRepository<ProjectJiraIssue, UUID> {

    List<ProjectJiraIssue> findAllByProjectId(UUID projectId);

    @Modifying
    void deleteAllByProjectIdAndIssueKeyNotIn(UUID projectId, List<String> issueKeys);

    @Modifying
    void deleteAllByProjectId(UUID projectId);

    @Query("SELECT MAX(i.syncedAt) FROM ProjectJiraIssue i WHERE i.projectId = :projectId")
    Optional<Instant> findMaxSyncedAtByProjectId(@Param("projectId") UUID projectId);
}
