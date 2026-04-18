package com.supervisesuite.backend.meetings.repository;

import com.supervisesuite.backend.meetings.entity.ProjectMeetingRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMeetingRecordRepository extends JpaRepository<ProjectMeetingRecord, UUID> {

    @Query("""
        SELECT r
        FROM ProjectMeetingRecord r
        WHERE r.projectId = :projectId
        ORDER BY
          CASE WHEN r.status = 'PENDING' THEN 0 ELSE 1 END,
          r.meetingDate DESC,
          r.createdAt DESC
        """)
    List<ProjectMeetingRecord> findByProjectIdPendingFirst(@Param("projectId") UUID projectId);

    Optional<ProjectMeetingRecord> findByIdAndProjectId(UUID id, UUID projectId);
}

