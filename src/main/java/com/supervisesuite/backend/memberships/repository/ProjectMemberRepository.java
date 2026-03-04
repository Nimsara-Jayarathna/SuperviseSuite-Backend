package com.supervisesuite.backend.memberships.repository;

import com.supervisesuite.backend.memberships.entity.ProjectMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    long countByProjectId(UUID projectId);

    List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
}
