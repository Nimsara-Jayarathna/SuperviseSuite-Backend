package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectRepositoryLinkContributor;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepositoryLinkContributorRepository extends JpaRepository<ProjectRepositoryLinkContributor, UUID> {
    Page<ProjectRepositoryLinkContributor> findByProjectRepositoryLinkIdOrderByCommitCountDescContributorNameAsc(
        UUID projectRepositoryLinkId,
        Pageable pageable
    );

    Optional<ProjectRepositoryLinkContributor> findByProjectRepositoryLinkIdAndContributorName(
        UUID projectRepositoryLinkId,
        String contributorName
    );

    @Modifying
    @Query("""
        delete from ProjectRepositoryLinkContributor contributor
        where contributor.projectRepositoryLinkId = :projectRepositoryLinkId
          and contributor.contributorName not in :names
    """)
    int deleteStaleContributors(
        @Param("projectRepositoryLinkId") UUID projectRepositoryLinkId,
        @Param("names") Collection<String> names
    );

    void deleteByProjectRepositoryLinkId(UUID projectRepositoryLinkId);
}
