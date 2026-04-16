package com.supervisesuite.backend.meetings.repository;

import com.supervisesuite.backend.meetings.entity.ProjectMeetingChannel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMeetingChannelRepository extends JpaRepository<ProjectMeetingChannel, UUID> {

    @Query("""
        SELECT c
        FROM ProjectMeetingChannel c
        WHERE c.projectId = :projectId
        ORDER BY
          CASE WHEN c.status = 'PENDING' THEN 0 ELSE 1 END,
          c.createdAt DESC
        """)
    List<ProjectMeetingChannel> findByProjectIdPendingFirst(@Param("projectId") UUID projectId);

    Optional<ProjectMeetingChannel> findByIdAndProjectId(UUID id, UUID projectId);
}

